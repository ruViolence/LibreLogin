/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.velocity.api.event;

import com.velocitypowered.api.proxy.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.database.User;

public class PreAuthorizationEvent extends TaskEvent {
    private final @NotNull Player player;
    private final @Nullable User user;
    private final @NotNull Reason reason;

    public PreAuthorizationEvent(@NotNull Player player, @Nullable User user, @NotNull Reason reason) {
        this.player = player;
        this.user = user;
        this.reason = reason;
    }
    
    public @NotNull Player getPlayer() {
        return player;
    }
    
    public @Nullable User getUser() {
        return user;
    }
    
    public @NotNull Reason getReason() {
        return reason;
    }
    
    public enum Reason {
        PREMIUM,
        SESSION,
        NONE
    }
}
