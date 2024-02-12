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
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.event.events.AuthenticatedEvent;
import xyz.kyngs.librelogin.common.command.ErrorThenKickException;
import xyz.kyngs.librelogin.common.command.InvalidCommandArgument;
import xyz.kyngs.librelogin.velocity.VelocityBootstrap;
import xyz.kyngs.librelogin.velocity.api.event.PostAuthorizationEvent;
import xyz.kyngs.librelogin.velocity.api.event.PostRegisterEvent;
import xyz.kyngs.librelogin.velocity.api.event.TaskEvent;

import java.util.Collection;
import java.util.Optional;

public class RegisterCommand extends ALibreCommand implements SimpleCommand {
    private static final @NotNull Component WRONG_USAGE = MiniMessage.miniMessage().deserialize("<red>Правильное использование: /register <пароль> <повтор пароля>");

    public RegisterCommand(VelocityBootstrap velocityBootstrap) {
        super(velocityBootstrap);
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource sender = invocation.source();
        if (!(sender instanceof Player player)) return;

        String[] args = invocation.arguments();

        runAsync(sender, () -> {
            String password = args.length > 0 ? args[0] : null;
            if (password == null) throw new InvalidCommandArgument(WRONG_USAGE);

            String passwordRepeat = args.length > 1 ? args[1] : null;
            if (passwordRepeat == null) throw new InvalidCommandArgument(WRONG_USAGE);

            if (!password.contentEquals(passwordRepeat))
                throw new InvalidCommandArgument(getMessage("error-password-not-match"));

            checkUnauthorized(player);
            {
                User invalidCaseUser = velocityBootstrap.getLibreLogin().checkInvalidCaseUsername(player.getUsername());
                if (invalidCaseUser != null) {
                    TextComponent message = velocityBootstrap.getLibreLogin().getMessages().getMessage("kick-invalid-case-username",
                            "%username%", invalidCaseUser.getLastNickname(),
                            "%wrong_username%", player.getUsername()
                    );
                    throw new ErrorThenKickException(message);
                }
            }
            var user = getUser(player);

            if (user.isRegistered()) throw new InvalidCommandArgument(getMessage("error-already-registered"));

            setPassword(user, password);

            try {
                PostRegisterEvent event = velocityBootstrap.getServer().getEventManager().fire(new PostRegisterEvent(player, user)).get();

                if (event.getResult() == TaskEvent.Result.NORMAL || event.getResult() == TaskEvent.Result.BYPASS) {
                    sender.sendMessage(getMessage("info-registered"));
                    velocityBootstrap.getLibreLogin().getAuthorizationProvider().authorize(user, player, AuthenticatedEvent.AuthenticationReason.REGISTER);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) return false;

        Optional<ServerConnection> currentServer = player.getCurrentServer();
        if (currentServer.isEmpty()) return true;

        Collection<RegisteredServer> limboServers = velocityBootstrap.getLibreLogin().getServerHandler().getLimboServers();
        return currentServer.map(ServerConnection::getServer).filter(limboServers::contains).isPresent();
    }
}
