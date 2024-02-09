/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;
import xyz.kyngs.librelogin.api.event.events.WrongPasswordEvent;
import xyz.kyngs.librelogin.common.command.InvalidCommandArgument;
import xyz.kyngs.librelogin.common.event.events.AuthenticPasswordChangeEvent;
import xyz.kyngs.librelogin.common.event.events.AuthenticWrongPasswordEvent;
import xyz.kyngs.librelogin.velocity.VelocityBootstrap;

import java.util.Collection;
import java.util.Optional;

public class ChangePasswordCommand extends ALibreCommand implements SimpleCommand {
    private static final @NotNull Component WRONG_USAGE = MiniMessage.miniMessage().deserialize("<red>Правильное использование: /changepassword <старый пароль> <новый пароль>");
    private static final @NotNull Component SUCCESS_CHANGE = MiniMessage.miniMessage().deserialize("<#32B900>Пароль был успешно изменен!");

    public ChangePasswordCommand(VelocityBootstrap velocityBootstrap) {
        super(velocityBootstrap);
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource sender = invocation.source();
        if (!(sender instanceof Player player)) return;

        String[] args = invocation.arguments();

        runAsync(sender, () -> {
            String oldPass = args.length > 0 ? args[0] : null;
            if (oldPass == null) throw new InvalidCommandArgument(WRONG_USAGE);

            String newPass = args.length > 1 ? args[1] : null;
            if (newPass == null) throw new InvalidCommandArgument(WRONG_USAGE);

            var user = getUser(player);

            if (!user.isRegistered()) {
                throw new InvalidCommandArgument(getMessage("error-no-password"));
            }

            var hashed = user.getHashedPassword();
            var crypto = getCrypto(hashed);

            if (!crypto.matches(oldPass, hashed)) {
                velocityBootstrap.getLibreLogin().getEventProvider()
                        .unsafeFire(velocityBootstrap.getLibreLogin().getEventTypes().wrongPassword,
                                new AuthenticWrongPasswordEvent<>(user, player, velocityBootstrap.getLibreLogin(), WrongPasswordEvent.AuthenticationSource.CHANGE_PASSWORD));
                throw new InvalidCommandArgument(getMessage("error-password-wrong"));
            }

            setPassword(user, newPass);

            velocityBootstrap.getLibreLogin().getDatabaseProvider().updateUser(user);

            sender.sendMessage(SUCCESS_CHANGE);
            velocityBootstrap.getLibreLogin().getEventProvider().unsafeFire(velocityBootstrap.getLibreLogin().getEventTypes().passwordChange, new AuthenticPasswordChangeEvent<>(user, player, velocityBootstrap.getLibreLogin(), hashed));
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) return false;

        Optional<ServerConnection> currentServer = player.getCurrentServer();
        if (currentServer.isEmpty()) return false;

        Collection<RegisteredServer> limboServers = velocityBootstrap.getLibreLogin().getServerHandler().getLimboServers();
        return currentServer.map(ServerConnection::getServer).filter(limboServers::contains).isEmpty();
    }
}
