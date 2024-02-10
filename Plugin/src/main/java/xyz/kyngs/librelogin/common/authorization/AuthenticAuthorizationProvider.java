/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.authorization;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import xyz.kyngs.librelogin.api.authorization.AuthorizationProvider;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.event.events.AuthenticatedEvent;
import xyz.kyngs.librelogin.common.AuthenticHandler;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.common.event.events.AuthenticAuthenticatedEvent;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class AuthenticAuthorizationProvider<P, S> extends AuthenticHandler<P, S> implements AuthorizationProvider<P> {

    private final Map<P, Boolean> unAuthorized;
    private final Cache<UUID, EmailVerifyData> emailConfirmCache;
    private final Cache<UUID, String> passwordResetCache;

    public AuthenticAuthorizationProvider(AuthenticLibreLogin<P, S> plugin) {
        super(plugin);
        unAuthorized = new ConcurrentHashMap<>();

        var millis = plugin.getConfiguration().get(ConfigurationKeys.MILLISECONDS_TO_REFRESH_NOTIFICATION);

        if (millis > 0) {
            plugin.repeat(this::notifyUnauthorized, 0, millis);
        }

        plugin.repeat(this::broadcastActionbars, 0, 1000);

        emailConfirmCache = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();

        passwordResetCache = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();
    }

    public Cache<UUID, EmailVerifyData> getEmailConfirmCache() {
        return emailConfirmCache;
    }

    public Cache<UUID, String> getPasswordResetCache() {
        return passwordResetCache;
    }

    public void onExit(P player) {
        stopTracking(player);
        emailConfirmCache.invalidate(platformHandle.getUUIDForPlayer(player));
        passwordResetCache.invalidate(platformHandle.getUUIDForPlayer(player));
    }

    @Override
    public boolean isAuthorized(P player) {
        return !unAuthorized.containsKey(player);
    }

    @Override
    public void authorize(User user, P player, AuthenticatedEvent.AuthenticationReason reason) {
        if (isAuthorized(player)) {
            throw new IllegalStateException("Player is already authorized");
        }
        stopTracking(player);

        user.setLastAuthentication(Timestamp.valueOf(LocalDateTime.now()));
        user.setIp(platformHandle.getIP(player));
        plugin.getDatabaseProvider().updateUser(user);

        var audience = platformHandle.getAudienceForPlayer(player);

        audience.clearTitle();
        audience.sendActionBar(Component.empty());
        plugin.getEventProvider().fire(plugin.getEventTypes().authenticated, new AuthenticAuthenticatedEvent<>(user, player, plugin, reason));
        plugin.authorize(player, user, audience);
    }

    public void startTracking(User user, P player) {
        var audience = platformHandle.getAudienceForPlayer(player);

        unAuthorized.put(player, user.isRegistered());

        plugin.cancelOnExit(plugin.delay(() -> {
            if (!unAuthorized.containsKey(player)) return;
            sendInfoMessage(user.isRegistered(), audience);
        }, 250), player);

        var limit = plugin.getConfiguration().get(ConfigurationKeys.SECONDS_TO_AUTHORIZE);

        if (limit > 0) {
            plugin.cancelOnExit(plugin.delay(() -> {
                if (!unAuthorized.containsKey(player)) return;
                platformHandle.kick(player, plugin.getMessages().getMessage("kick-time-limit"));
            }, limit * 1000L), player);
        }
    }

    private void broadcastActionbars() {
        var wrong = new HashSet<P>();
        unAuthorized.forEach((player, registered) -> {
            var audience = platformHandle.getAudienceForPlayer(player);

            if (audience == null) {
                wrong.add(player);
                return;
            }

            sendActionBar(registered, audience);

        });

        wrong.forEach(unAuthorized::remove);
    }

    private void sendActionBar(boolean registered, Audience audience) {
        if (plugin.getConfiguration().get(ConfigurationKeys.USE_ACTION_BAR)) {
            audience.sendActionBar(plugin.getMessages().getMessage(registered ? "action-bar-login" : "action-bar-register"));
        }
    }

    private void sendInfoMessage(boolean registered, Audience audience) {
        audience.sendMessage(plugin.getMessages().getMessage(registered ? "prompt-login" : "prompt-register"));
        if (!plugin.getConfiguration().get(ConfigurationKeys.USE_TITLES)) return;
        var toRefresh = plugin.getConfiguration().get(ConfigurationKeys.MILLISECONDS_TO_REFRESH_NOTIFICATION);
        //noinspection UnstableApiUsage
        audience.showTitle(Title.title(
                plugin.getMessages().getMessage(registered ? "title-login" : "title-register"),
                plugin.getMessages().getMessage(registered ? "sub-title-login" : "sub-title-register"),
                Title.Times.of(
                        Duration.ofMillis(0),
                        Duration.ofMillis(toRefresh > 0 ?
                                (long) (toRefresh * 1.1) :
                                10000
                        ),
                        Duration.ofMillis(0)
                )
        ));
    }

    public void stopTracking(P player) {
        unAuthorized.remove(player);
    }

    public void notifyUnauthorized() {
        var wrong = new HashSet<P>();
        unAuthorized.forEach((player, registered) -> {
            var audience = platformHandle.getAudienceForPlayer(player);

            if (audience == null) {
                wrong.add(player);
                return;
            }

            sendInfoMessage(registered, audience);

        });

        wrong.forEach(unAuthorized::remove);
    }

    public record EmailVerifyData(String email, String token, UUID uuid) {
    }
}
