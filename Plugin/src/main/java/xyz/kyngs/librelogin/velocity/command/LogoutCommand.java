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
import xyz.kyngs.librelogin.common.command.InvalidCommandArgument;
import xyz.kyngs.librelogin.velocity.VelocityBootstrap;

import java.util.Collection;
import java.util.Optional;

public class LogoutCommand extends ALibreCommand implements SimpleCommand {
    private static final @NotNull Component SUCCESS_LOGOUT = MiniMessage.miniMessage().deserialize("<yellow>Ваша сессия была закрыта. Вам нужно будет авторизоваться при следующем заходе на сервер.");

    public LogoutCommand(VelocityBootstrap velocityBootstrap) {
        super(velocityBootstrap);
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource sender = invocation.source();
        if (!(sender instanceof Player player)) return;

        runAsync(sender, () -> {
            var user = getUser(player);

            if (!user.isRegistered()) {
                throw new InvalidCommandArgument(getMessage("error-no-password"));
            }

            user.setLastAuthentication(null);

            velocityBootstrap.getLibreLogin().getDatabaseProvider().updateUser(user);

            sender.sendMessage(SUCCESS_LOGOUT);
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
