/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, JBoss Inc., and individual contributors as indicated
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

package org.xnio;

import java.io.IOException;
import java.nio.channels.Channel;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.jboss.logging.Logger;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedChannel;

/**
 * Channel listener utility methods.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@SuppressWarnings("unused")
public final class ChannelListeners {

    private static final ChannelListener<Channel> NULL_LISTENER = new ChannelListener<Channel>() {
        public void handleEvent(final Channel channel) {
        }
    };
    private static final ChannelListener.Setter<?> NULL_SETTER = new ChannelListener.Setter<Channel>() {
        public void set(final ChannelListener<? super Channel> channelListener) {
        }
    };
    private static final Logger listenerLog = Logger.getLogger("org.xnio.listener");
    private static ChannelListener<Channel> CLOSING_CHANNEL_LISTENER = new ChannelListener<Channel>() {
        public void handleEvent(final Channel channel) {
            IoUtils.safeClose(channel);
        }
    };

    private ChannelListeners() {
    }

    /**
     * Invoke a channel listener on a given channel, logging any errors.
     *
     * @param channel the channel
     * @param channelListener the channel listener
     * @param <T> the channel type
     * @return {@code true} if the listener completed successfully, or {@code false} if it failed
     */
    public static <T extends Channel> boolean invokeChannelListener(T channel, ChannelListener<? super T> channelListener) {
        if (channelListener != null) try {
            listenerLog.tracef("Invoking listener %s on channel %s", channelListener, channel);
            channelListener.handleEvent(channel);
        } catch (Throwable t) {
            listenerLog.errorf(t, "A channel event listener threw an exception");
            return false;
        }
        return true;
    }

    /**
     * Invoke a channel listener on a given channel, logging any errors, using the given executor.
     *
     * @param executor the executor
     * @param channel the channel
     * @param channelListener the channel listener
     * @param <T> the channel type
     */
    public static <T extends Channel> void invokeChannelListener(Executor executor, T channel, ChannelListener<? super T> channelListener) {
        try {
            executor.execute(getChannelListenerTask(channel, channelListener));
        } catch (RejectedExecutionException ree) {
            invokeChannelListener(channel, channelListener);
        }
    }

    /**
     * Get a task which invokes the given channel listener on the given channel.
     *
     * @param channel the channel
     * @param channelListener the channel listener
     * @param <T> the channel type
     * @return the runnable task
     */
    public static <T extends Channel> Runnable getChannelListenerTask(final T channel, final ChannelListener<? super T> channelListener) {
        return new Runnable() {
            public void run() {
                invokeChannelListener(channel, channelListener);
            }
        };
    }

    /**
     * Get a channel listener which closes the channel when notified.
     *
     * @return the channel listener
     */
    public static ChannelListener<Channel> closingChannelListener() {
        return CLOSING_CHANNEL_LISTENER;
    }

    /**
     * Get a channel listener which does nothing.
     *
     * @return the null channel listener
     */
    public static ChannelListener<Channel> nullChannelListener() {
        return NULL_LISTENER;
    }

    /**
     * Create an open listener adapter which automatically accepts connections and invokes an open listener.
     *
     * @param openListener the channel open listener
     * @param <C> the connected channel type
     * @return a channel accept listener
     */
    public static <C extends ConnectedChannel> ChannelListener<AcceptingChannel<C>> openListenerAdapter(final ChannelListener<? super C> openListener) {
        if (openListener == null) {
            throw new IllegalArgumentException("openListener is null");
        }
        return new ChannelListener<AcceptingChannel<C>>() {
            public void handleEvent(final AcceptingChannel<C> channel) {
                try {
                    final C accepted = channel.accept();
                    if (accepted != null) {
                        invokeChannelListener(accepted, openListener);
                    }
                } catch (IOException e) {
                    listenerLog.errorf("Failed to accept a connection on %s: %s", channel, e);
                }
            }

            public String toString() {
                return "Accepting listener for " + openListener;
            }
        };
    }

    /**
     * Get a setter based on an atomic reference field updater.  Used by channel implementations to avoid having to
     * define an anonymous class for each listener field.
     *
     * @param channel the channel
     * @param updater the updater
     * @param <T> the channel type
     * @param <C> the holding class
     * @return the setter
     */
    public static <T extends Channel, C> ChannelListener.Setter<T> getSetter(final C channel, final AtomicReferenceFieldUpdater<C, ChannelListener> updater) {
        return new ChannelListener.Setter<T>() {
            public void set(final ChannelListener<? super T> channelListener) {
                updater.set(channel, channelListener);
            }
        };
    }

    /**
     * Get a setter based on an atomic reference.  Used by channel implementations to avoid having to
     * define an anonymous class for each listener field.
     *
     * @param atomicReference the atomic reference
     * @param <T> the channel type
     * @return the setter
     */
    public static <T extends Channel> ChannelListener.Setter<T> getSetter(final AtomicReference<ChannelListener<? super T>> atomicReference) {
        return new ChannelListener.Setter<T>() {
            public void set(final ChannelListener<? super T> channelListener) {
                atomicReference.set(channelListener);
            }
        };
    }

    /**
     * Get a channel listener setter which delegates to the given target setter with a different channel type.
     *
     * @param target the target setter
     * @param realChannel the channel to send in to the listener
     * @param <T> the real channel type
     * @return the delegating setter
     */
    public static <T extends Channel> ChannelListener.Setter<T> getDelegatingSetter(final ChannelListener.Setter<? extends Channel> target, final T realChannel) {
        return target == null ? null : delegatingSetter(target, realChannel);
    }

    private static <T extends Channel, O extends Channel> DelegatingSetter<T, O> delegatingSetter(final ChannelListener.Setter<O> setter, final T realChannel) {
        return new DelegatingSetter<T,O>(setter, realChannel);
    }

    /**
     * Get a channel listener setter which does nothing.
     *
     * @param <T> the channel type
     * @return a setter which does nothing
     */
    @SuppressWarnings({ "unchecked" })
    public static <T extends Channel> ChannelListener.Setter<T> nullSetter() {
        return (ChannelListener.Setter<T>) NULL_SETTER;
    }

    /**
     * Get a channel listener which executes a delegate channel listener via an executor.  If an exception occurs
     * submitting the task, the associated channel is closed.
     *
     * @param listener the listener to invoke
     * @param executor the executor with which to invoke the listener
     * @param <T> the channel type
     * @return a delegating channel listener
     */
    public static <T extends Channel> ChannelListener<T> executorChannelListener(final ChannelListener<T> listener, final Executor executor) {
        return new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                try {
                    executor.execute(getChannelListenerTask(channel, listener));
                } catch (RejectedExecutionException e) {
                    listenerLog.errorf("Failed to submit task to executor: %s (closing %s)", e, channel);
                    IoUtils.safeClose(channel);
                }
            }
        };
    }

    private static class DelegatingSetter<T extends Channel, O extends Channel> implements ChannelListener.Setter<T> {
        private final ChannelListener.Setter<O> setter;
        private final T realChannel;

        DelegatingSetter(final ChannelListener.Setter<O> setter, final T realChannel) {
            this.setter = setter;
            this.realChannel = realChannel;
        }

        public void set(final ChannelListener<? super T> channelListener) {
            setter.set(channelListener == null ? null : new DelegatingChannelListener<T, O>(channelListener, realChannel));
        }
    }

    private static class DelegatingChannelListener<T extends Channel, O extends Channel> implements ChannelListener<O> {

        private final ChannelListener<? super T> channelListener;
        private final T realChannel;

        public DelegatingChannelListener(final ChannelListener<? super T> channelListener, final T realChannel) {
            this.channelListener = channelListener;
            this.realChannel = realChannel;
        }

        public void handleEvent(final Channel channel) {
            channelListener.handleEvent(realChannel);
        }
    }
}
