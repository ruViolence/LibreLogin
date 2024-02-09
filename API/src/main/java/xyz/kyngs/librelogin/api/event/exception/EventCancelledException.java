/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.event.exception;

/**
 * An exception thrown when an event is cancelled.
 */
public class EventCancelledException extends RuntimeException {
    public static final EventCancelledException INSTANCE = new EventCancelledException();

    private EventCancelledException() {
    }

    @Override
    public Throwable initCause(Throwable cause) {
        return this;
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}