/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.velocity.api.event;

import org.jetbrains.annotations.NotNull;

public class TaskEvent {
    private Result result = Result.NORMAL; 
    
    public @NotNull Result getResult() {
        return result;
    }
    
    public void setResult(@NotNull Result result) {
        this.result = result;
    }
    
    public enum Result {
        NORMAL,
        WAIT,
        BYPASS
    }
}
