/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.listener;

import com.velocitypowered.api.proxy.Player;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.BiHolder;
import xyz.kyngs.librelogin.api.PlatformHandle;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.authorization.ProfileConflictResolutionStrategy;
import xyz.kyngs.librelogin.common.command.ErrorThenKickException;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.velocity.VelocityLibreLogin;
import xyz.kyngs.librelogin.velocity.api.event.PreAuthorizationEvent;
import xyz.kyngs.librelogin.velocity.api.event.TaskEvent;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

public class AuthenticListeners<Plugin extends AuthenticLibreLogin<P, S>, P, S> {

    @SuppressWarnings("RegExpSimplifiable") //I don't believe you
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");

    protected final Plugin plugin;
    protected final PlatformHandle<P, S> platformHandle;

    public AuthenticListeners(Plugin plugin) {
        this.plugin = plugin;
        platformHandle = plugin.getPlatformHandle();
    }

    protected void onPostLogin(P player, User user, VelocityLibreLogin velocityPlugin) {
        var ip = platformHandle.getIP(player);
        var uuid = platformHandle.getUUIDForPlayer(player);
        if (plugin.fromFloodgate(uuid)) return;

        if (user == null) {
            user = plugin.getDatabaseProvider().getByUUID(uuid);
        }

        // Player is not registered yet
        if (user == null || !user.isRegistered()) {
            plugin.getAuthorizationProvider().startTracking(player, false);
            return;
        }

        var sessionTime = Duration.ofSeconds(plugin.getConfiguration().get(ConfigurationKeys.SESSION_TIMEOUT));
        
        boolean autoLoginEnabled = user.autoLoginEnabled();
        boolean hasSession = sessionTime != null && user.getLastAuthentication() != null && ip.equals(user.getIp()) && user.getLastAuthentication().toLocalDateTime().plus(sessionTime).isAfter(LocalDateTime.now());
        PreAuthorizationEvent.Reason reason;

        if (autoLoginEnabled) {
            reason = PreAuthorizationEvent.Reason.PREMIUM;
        } else if (hasSession) {
            reason = PreAuthorizationEvent.Reason.SESSION;
        } else {
            reason = PreAuthorizationEvent.Reason.NONE;
        }

        try {
            PreAuthorizationEvent event = velocityPlugin.getServer().getEventManager().fire(new PreAuthorizationEvent((Player) player, user, reason)).get();

            if (event.getResult() == TaskEvent.Result.WAIT) {
                plugin.getAuthorizationProvider().startTracking(player, true);
            } else {
                if (autoLoginEnabled) {
                    plugin.delay(() -> plugin.getPlatformHandle().getAudienceForPlayer(player).sendMessage(plugin.getMessages().getMessage("info-premium-logged-in")), 500);
                } else if (hasSession || event.getResult() == TaskEvent.Result.BYPASS) {
                    if (event.getResult() == TaskEvent.Result.BYPASS) user.setLastAuthentication(Timestamp.valueOf(LocalDateTime.now()));
                    plugin.delay(() -> plugin.getPlatformHandle().getAudienceForPlayer(player).sendMessage(plugin.getMessages().getMessage("info-session-logged-in")), 500);
                } else {
                    plugin.getAuthorizationProvider().startTracking(player, true);
                }
            }

            user.setLastSeen(Timestamp.valueOf(LocalDateTime.now()));

            var finalUser = user;
            plugin.delay(() -> plugin.getDatabaseProvider().updateUser(finalUser), 0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    protected void onPlayerDisconnect(P player) {
        plugin.onExit(player);
        plugin.getAuthorizationProvider().onExit(player);
    }

    protected PreLoginResult onPreLogin(String username, InetAddress address) {
        if (username.length() < 3 || username.length() > 16 || !NAME_PATTERN.matcher(username).matches()) {
            return new PreLoginResult(PreLoginState.DENIED, plugin.getMessages().getMessage("kick-illegal-username"), null);
        }

        var user = plugin.getDatabaseProvider().getByName(username);

        try {
            plugin.checkInvalidCaseUsername(username, user);
        } catch (ErrorThenKickException e) {
            return new PreLoginResult(PreLoginState.DENIED, e.getReason(), null);
        }

        // Player is registered and has enabled autologin; handle them forcibly as an online player
        if (user != null && user.autoLoginEnabled()) {
            return new PreLoginResult(PreLoginState.FORCE_ONLINE, null, user);
        }

        // Player is not registered yet; check IP limit and handle them as an offline player
        if (user == null) {
            try {
                plugin.checkIpLimit(address);
            } catch (ErrorThenKickException e) {
                return new PreLoginResult(PreLoginState.DENIED, e.getReason(), null);
            }
        }

        return new PreLoginResult(PreLoginState.FORCE_OFFLINE, null, null);
    }

    private PreLoginResult handleProfileConflict(User conflicting, User conflicted) {
        return switch (ProfileConflictResolutionStrategy.valueOf(plugin.getConfiguration().get(ConfigurationKeys.PROFILE_CONFLICT_RESOLUTION_STRATEGY))) {
            case BLOCK -> new PreLoginResult(PreLoginState.DENIED, plugin.getMessages().getMessage("kick-name-mismatch",
                    "%nickname%", conflicting.getLastNickname()
            ), null);
            case USE_OFFLINE -> new PreLoginResult(PreLoginState.FORCE_OFFLINE, null, null);
            case OVERWRITE -> {
                plugin.getDatabaseProvider().deleteUser(conflicted);
                conflicting.setLastNickname(conflicted.getLastNickname());
                plugin.getDatabaseProvider().updateUser(conflicting);
                yield new PreLoginResult(PreLoginState.FORCE_ONLINE, null, conflicting);
            }
        };

    }

    protected BiHolder<Boolean, S> chooseServer(P player, @Nullable String ip, @Nullable User user) {
        var id = platformHandle.getUUIDForPlayer(player);
        var fromFloodgate = plugin.fromFloodgate(id);

        if (fromFloodgate) {
            user = null;
        } else if (user == null) {
            user = plugin.getDatabaseProvider().getByUUID(id);
        }

        if (plugin.getAuthorizationProvider().isAuthorized(player)) {
            return new BiHolder<>(true, plugin.getServerHandler().chooseLobbyServer(user, player, true, false));
        } else {
            return new BiHolder<>(false, plugin.getServerHandler().chooseLimboServer(user, player));
        }
    }
}
