/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.velocity.command;

import co.aikar.commands.MessageKeys;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.TextComponent;
import org.jetbrains.annotations.NotNull;
import xyz.kyngs.librelogin.api.crypto.CryptoProvider;
import xyz.kyngs.librelogin.api.crypto.HashedPassword;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.command.InvalidCommandArgument;
import xyz.kyngs.librelogin.common.event.events.AuthenticPremiumLoginSwitchEvent;
import xyz.kyngs.librelogin.common.util.GeneralUtil;
import xyz.kyngs.librelogin.velocity.VelocityBootstrap;

public abstract class ALibreCommand {
    protected final VelocityBootstrap velocityBootstrap;

    public ALibreCommand(VelocityBootstrap velocityBootstrap) {
        this.velocityBootstrap = velocityBootstrap;
    }

    protected CryptoProvider getCrypto(HashedPassword password) {
        return velocityBootstrap.getLibreLogin().getCryptoProvider(password.algo());
    }

    public void runAsync(CommandSource sender, Runnable runnable) {
        GeneralUtil.runAsync(() -> {
            if (sender instanceof Player player && velocityBootstrap.getLibreLogin().getLimiter().tryAndLimit(player.getUniqueId()))
                throw new InvalidCommandArgument(velocityBootstrap.getLibreLogin().getMessages().getMessage("error-throttle"));
            runnable.run();
        }).exceptionally(throwable -> {
            if (throwable instanceof InvalidCommandArgument ica) {
                sender.sendMessage(ica.getUserFuckUp());
            }
            return null;
        });
    }

    protected TextComponent getMessage(String key, String... replacements) {
        return velocityBootstrap.getLibreLogin().getMessages().getMessage(key, replacements);
    }

    protected @NotNull User getUserOtherWiseInform(String name) {
        var user = velocityBootstrap.getLibreLogin().getDatabaseProvider().getByName(name);

        if (user == null) throw new InvalidCommandArgument(getMessage("error-unknown-user"));

        return user;
    }

    protected void requireOffline(User user) {
        if (velocityBootstrap.getLibreLogin().isPresent(user.getUuid()))
            throw new InvalidCommandArgument(getMessage("error-player-online"));
    }

    protected Player requireOnline(User user) {
        if (velocityBootstrap.getLibreLogin().multiProxyEnabled())
            throw new InvalidCommandArgument(getMessage("error-not-available-on-multi-proxy"));
        var player = velocityBootstrap.getLibreLogin().getPlayerForUUID(user.getUuid());
        if (player == null)
            throw new InvalidCommandArgument(getMessage("error-player-offline"));
        return player;
    }

    protected void requireUnAuthorized(Player player) {
        if (velocityBootstrap.getLibreLogin().getAuthorizationProvider().isAuthorized(player))
            throw new InvalidCommandArgument(getMessage("error-player-authorized"));
    }

    protected void requireRegistered(User user) {
        if (!user.isRegistered())
            throw new InvalidCommandArgument(getMessage("error-player-not-registered"));
    }

    public static <P> void enablePremium(P player, User user, AuthenticLibreLogin<P, ?> plugin) {
        var id = plugin.getUserOrThrowICA(user.getLastNickname());

        if (id == null) throw new InvalidCommandArgument(plugin.getMessages().getMessage("error-not-paid"));

        user.setPremiumUUID(id.uuid());

        plugin.getEventProvider().unsafeFire(plugin.getEventTypes().premiumLoginSwitch, new AuthenticPremiumLoginSwitchEvent<>(user, player, plugin));
    }

    protected User getUser(Player player) {
        if (player == null)
            throw new co.aikar.commands.InvalidCommandArgument(MessageKeys.NOT_ALLOWED_ON_CONSOLE, false);

        var uuid = velocityBootstrap.getLibreLogin().getPlatformHandle().getUUIDForPlayer(player);

        if (velocityBootstrap.getLibreLogin().fromFloodgate(uuid))
            throw new InvalidCommandArgument(getMessage("error-from-floodgate"));

        return velocityBootstrap.getLibreLogin().getDatabaseProvider().getByUUID(uuid);
    }

    protected void setPassword(User user, String password) {
        if (!velocityBootstrap.getLibreLogin().validPassword(password))
            throw new InvalidCommandArgument(getMessage("error-forbidden-password"));

        var defaultProvider = velocityBootstrap.getLibreLogin().getDefaultCryptoProvider();

        var hash = defaultProvider.createHash(password);

        if (hash == null) {
            throw new InvalidCommandArgument(getMessage("error-password-too-long"));
        }

        user.setHashedPassword(hash);
    }

    protected void checkUnauthorized(Player player) {
        if (velocityBootstrap.getLibreLogin().getAuthorizationProvider().isAuthorized(player)) {
            throw new InvalidCommandArgument(getMessage("error-already-authorized"));
        }
    }
}
