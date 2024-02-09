/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import xyz.kyngs.librelogin.api.configuration.CorruptedConfigurationException;
import xyz.kyngs.librelogin.api.event.events.AuthenticatedEvent;
import xyz.kyngs.librelogin.common.command.InvalidCommandArgument;
import xyz.kyngs.librelogin.common.event.events.AuthenticPasswordChangeEvent;
import xyz.kyngs.librelogin.common.event.events.AuthenticPremiumLoginSwitchEvent;
import xyz.kyngs.librelogin.common.util.GeneralUtil;
import xyz.kyngs.librelogin.velocity.VelocityBootstrap;

import java.io.IOException;

import static xyz.kyngs.librelogin.common.AuthenticLibreLogin.DATE_TIME_FORMATTER;

public class LibreLoginCommand extends ALibreCommand implements SimpleCommand {
    public LibreLoginCommand(VelocityBootstrap velocityBootstrap) {
        super(velocityBootstrap);
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource sender = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            runAsync(sender, () -> sender.sendMessage(getMessage("info-about",
                    "%version%", velocityBootstrap.getLibreLogin().getVersion()
            )));
            return;
        }

        switch (args[0]) {
            case "reload_configuration" -> runAsync(sender, () -> {
                sender.sendMessage(getMessage("info-reloading"));

                try {
                    velocityBootstrap.getLibreLogin().getConfiguration().reload(velocityBootstrap.getLibreLogin());
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new InvalidCommandArgument(getMessage("error-unknown"));
                } catch (CorruptedConfigurationException e) {
                    var cause = GeneralUtil.getFurthestCause(e);
                    throw new InvalidCommandArgument(getMessage("error-corrupted-configuration",
                            "%cause%", "%s: %s".formatted(cause.getClass().getSimpleName(), cause.getMessage()))
                    );
                }

                sender.sendMessage(getMessage("info-reloaded"));
            });
            case "reload_messages" -> runAsync(sender, () -> {
                sender.sendMessage(getMessage("info-reloading"));

                try {
                    velocityBootstrap.getLibreLogin().getMessages().reload(velocityBootstrap.getLibreLogin());
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new InvalidCommandArgument(getMessage("error-unknown"));
                } catch (CorruptedConfigurationException e) {
                    var cause = GeneralUtil.getFurthestCause(e);
                    throw new InvalidCommandArgument(getMessage("error-corrupted-messages",
                            "%cause%", "%s: %s".formatted(cause.getClass().getSimpleName(), cause.getMessage()))
                    );
                }

                sender.sendMessage(getMessage("info-reloaded"));
            });
            case "user_info" -> runAsync(sender, () -> {
                String name = args.length > 1 ? args[1] : null;
                if (name == null) throw new InvalidCommandArgument(getMessage("autocomplete.user-info"));

                var user = getUserOtherWiseInform(name);

                sender.sendMessage(getMessage("info-user",
                        "%uuid%", user.getUuid().toString(),
                        "%premium_uuid%", user.getPremiumUUID() == null ? "N/A" : user.getPremiumUUID().toString(),
                        "%last_seen%", DATE_TIME_FORMATTER.format(user.getLastSeen().toLocalDateTime()),
                        "%joined%", DATE_TIME_FORMATTER.format(user.getJoinDate().toLocalDateTime()),
                        "%2fa%", user.getSecret() != null ? "Enabled" : "Disabled",
                        "%email%", user.getEmail() == null ? "N/A" : user.getEmail(),
                        "%ip%", user.getIp() == null ? "N/A" : user.getIp(),
                        "%last_authenticated%", user.getLastAuthentication() == null ? "N/A" : DATE_TIME_FORMATTER.format(user.getLastAuthentication().toLocalDateTime())
                ));
            });
            case "user_migrate" -> runAsync(sender, () -> {
                String name = args.length > 1 ? args[1] : null;
                if (name == null) throw new InvalidCommandArgument(getMessage("autocomplete.user-migrate"));

                String newName = args.length > 2 ? args[2] : null;
                if (newName == null) throw new InvalidCommandArgument(getMessage("autocomplete.user-migrate"));

                var user = getUserOtherWiseInform(name);
                var colliding = velocityBootstrap.getLibreLogin().getDatabaseProvider().getByName(newName);

                if (colliding != null && !colliding.getUuid().equals(user.getUuid()))
                    throw new InvalidCommandArgument(getMessage("error-occupied-user",
                            "%name%", newName
                    ));

                requireOffline(user);

                sender.sendMessage(getMessage("info-editing"));

                user.setLastNickname(newName);
                if (user.getPremiumUUID() != null) {
                    user.setPremiumUUID(null);
                    velocityBootstrap.getLibreLogin().getEventProvider().unsafeFire(velocityBootstrap.getLibreLogin().getEventTypes().premiumLoginSwitch, new AuthenticPremiumLoginSwitchEvent<>(user, null, velocityBootstrap.getLibreLogin()));
                }
                velocityBootstrap.getLibreLogin().getDatabaseProvider().updateUser(user);

                sender.sendMessage(getMessage("info-edited"));
            });
            case "user_unregister" -> runAsync(sender, () -> {
                String name = args.length > 1 ? args[1] : null;
                if (name == null) throw new InvalidCommandArgument(getMessage("autocomplete.user-unregister"));

                var user = getUserOtherWiseInform(name);

                requireOffline(user);

                sender.sendMessage(getMessage("info-editing"));

                user.setHashedPassword(null);
                user.setSecret(null);
                user.setIp(null);
                user.setLastAuthentication(null);
                user.setPremiumUUID(null);
                velocityBootstrap.getLibreLogin().getDatabaseProvider().updateUser(user);

                sender.sendMessage(getMessage("info-edited"));
            });
            case "user_delete" -> runAsync(sender, () -> {
                String name = args.length > 1 ? args[1] : null;
                if (name == null) throw new InvalidCommandArgument(getMessage("autocomplete.user-delete"));

                var user = getUserOtherWiseInform(name);

                requireOffline(user);

                sender.sendMessage(getMessage("info-deleting"));

                velocityBootstrap.getLibreLogin().getDatabaseProvider().deleteUser(user);

                sender.sendMessage(getMessage("info-deleted"));
            });
            case "user_premium" -> runAsync(sender, () -> {
                String name = args.length > 1 ? args[1] : null;
                if (name == null) throw new InvalidCommandArgument(getMessage("autocomplete.user-premium"));

                var user = getUserOtherWiseInform(name);

                requireOffline(user);

                sender.sendMessage(getMessage("info-editing"));

                enablePremium(null, user, velocityBootstrap.getLibreLogin());

                velocityBootstrap.getLibreLogin().getDatabaseProvider().updateUser(user);

                sender.sendMessage(getMessage("info-edited"));
            });
            case "user_cracked" -> runAsync(sender, () -> {
                String name = args.length > 1 ? args[1] : null;
                if (name == null) throw new InvalidCommandArgument(getMessage("autocomplete.user-cracked"));

                var user = getUserOtherWiseInform(name);

                requireOffline(user);

                sender.sendMessage(getMessage("info-editing"));

                user.setPremiumUUID(null);
                velocityBootstrap.getLibreLogin().getDatabaseProvider().updateUser(user);

                sender.sendMessage(getMessage("info-edited"));
            });
            case "user_login" -> runAsync(sender, () -> {
                String name = args.length > 1 ? args[1] : null;
                if (name == null) throw new InvalidCommandArgument(getMessage("autocomplete.user-login"));

                var user = getUserOtherWiseInform(name);

                var target = requireOnline(user);
                requireUnAuthorized(target);
                requireRegistered(user);

                sender.sendMessage(getMessage("info-logging-in"));

                velocityBootstrap.getLibreLogin().getAuthorizationProvider().authorize(user, target, AuthenticatedEvent.AuthenticationReason.LOGIN);

                sender.sendMessage(getMessage("info-logged-in"));
            });
            case "user_changepassword" -> runAsync(sender, () -> {
                String name = args.length > 1 ? args[1] : null;
                if (name == null) throw new InvalidCommandArgument(getMessage("autocomplete.user-pass-change"));

                String password = args.length > 2 ? args[2] : null;
                if (password == null) throw new InvalidCommandArgument(getMessage("autocomplete.user-pass-change"));

                var user = getUserOtherWiseInform(name);
                var old = user.getHashedPassword();

                setPassword(user, password);

                velocityBootstrap.getLibreLogin().getDatabaseProvider().updateUser(user);

                sender.sendMessage(getMessage("info-edited"));

                velocityBootstrap.getLibreLogin().getEventProvider().unsafeFire(velocityBootstrap.getLibreLogin().getEventTypes().passwordChange, new AuthenticPasswordChangeEvent<>(user, null, velocityBootstrap.getLibreLogin(), old));
            });
            case "user_alts" -> runAsync(sender, () -> {
                String name = args.length > 1 ? args[1] : null;
                if (name == null) throw new InvalidCommandArgument(getMessage("autocomplete.user-alts"));

                var user = getUserOtherWiseInform(name);

                var alts = velocityBootstrap.getLibreLogin().getDatabaseProvider().getByIP(user.getIp());

                if (alts.isEmpty()) {
                    sender.sendMessage(getMessage("info-no-alts"));
                    return;
                }

                sender.sendMessage(getMessage("info-alts",
                        "%count%", String.valueOf(alts.size())
                ));

                for (var alt : alts) {
                    sender.sendMessage(getMessage("info-alts-entry",
                            "%name%", alt.getLastNickname(),
                            "%last_seen%", DATE_TIME_FORMATTER.format(user.getLastSeen().toLocalDateTime())
                    ));
                }
            });
            default -> runAsync(sender, () -> sender.sendMessage(getMessage("info-about",
                    "%version%", velocityBootstrap.getLibreLogin().getVersion()
            )));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("librelogin.admin");
    }
}
