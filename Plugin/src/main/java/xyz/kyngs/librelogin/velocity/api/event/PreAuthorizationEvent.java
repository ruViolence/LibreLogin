/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.velocity.api.event;

import com.velocitypowered.api.proxy.Player;
import xyz.kyngs.librelogin.api.database.User;

public class PreAuthorizationEvent extends TaskEvent {
    private final Player player;
    private final User user;
    private final Reason reason;

    public PreAuthorizationEvent(Player player, User user, Reason reason) {
        this.player = player;
        this.user = user;
        this.reason = reason;
    }
    
    public Player getPlayer() {
        return player;
    }
    
    public User getUser() {
        return user;
    }
    
    public Reason getReason() {
        return reason;
    }
    
    public enum Reason {
        PREMIUM,
        SESSION,
        NONE
    }
}
