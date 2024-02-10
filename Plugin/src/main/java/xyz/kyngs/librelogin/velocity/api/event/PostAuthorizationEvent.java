/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.velocity.api.event;

import com.velocitypowered.api.proxy.Player;
import org.jetbrains.annotations.NotNull;
import xyz.kyngs.librelogin.api.database.User;

public class PostAuthorizationEvent extends TaskEvent {
    private final Player player;
    private final User user;

    public PostAuthorizationEvent(@NotNull Player player, @NotNull User user) {
        this.player = player;
        this.user = user;
    }
    
    public @NotNull Player getPlayer() {
        return player;
    }
    
    public @NotNull User getUser() {
        return user;
    }
}
