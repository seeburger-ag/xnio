/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.xnio.channels;

import java.io.IOException;
import java.nio.channels.InterruptibleChannel;
import org.xnio.ChannelListener;
import org.xnio.XnioWorker;

/**
 * A channel which is closeable.  A listener may be registered which is triggered (only once) on channel close.
 *
 * @since 2.0
 */
public interface CloseableChannel extends InterruptibleChannel, Configurable {
    /**
     * Get the setter which can be used to change the close listener for this channel.  If the channel is already
     * closed, then the listener will not be called.
     *
     * @return the setter
     */
    ChannelListener.Setter<? extends CloseableChannel> getCloseSetter();

    /**
     * Get the worker for this channel.
     *
     * @return the worker
     */
    XnioWorker getWorker();

    /**
     * Close this channel.  When a channel is closed, its close listener is invoked.  Invoking this method
     * more than once has no additional effect.
     *
     * @throws IOException if the close failed
     */
    void close() throws IOException;
}
