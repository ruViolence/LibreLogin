/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.velocity.api.event;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.event.CancellableEvent;

public class PreInfoSendEvent implements CancellableEvent {
    private final @NotNull Player player;
    private final boolean isRegistered;
    private @Nullable Component message;
    private @Nullable Title title;
    private boolean cancel = false; 

    public PreInfoSendEvent(@NotNull Player player, boolean isRegistered, @Nullable Component message, @Nullable Title title) {
        this.player = player;
        this.isRegistered = isRegistered;
        this.message = message;
        this.title = title;
    }

    public @NotNull Player getPlayer() {
        return player;
    }

    public boolean isRegistered() {
        return isRegistered;
    }

    public @Nullable Component getMessage() {
        return message;
    }

    public void setMessage(@Nullable Component message) {
        this.message = message;
    }

    public @Nullable Title getTitle() {
        return title;
    }

    public void setTitle(@Nullable Title title) {
        this.title = title;
    }

    @Override
    public boolean isCancelled() {
        return cancel;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancel = cancel;
    }
}
