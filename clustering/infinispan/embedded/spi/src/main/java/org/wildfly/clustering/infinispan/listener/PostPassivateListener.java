/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

package org.wildfly.clustering.infinispan.listener;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.infinispan.Cache;
import org.infinispan.commons.util.concurrent.CompletableFutures;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryPassivated;
import org.infinispan.notifications.cachelistener.event.CacheEntryPassivatedEvent;
import org.infinispan.notifications.cachelistener.filter.CacheEventFilter;
import org.infinispan.util.concurrent.BlockingManager;

/**
 * Generic non-blocking post-passivation listener that delegates to a blocking consumer.
 * @author Paul Ferraro
 */
public class PostPassivateListener<K, V> extends LifecycleListenerRegistration {

    public PostPassivateListener(Cache<K, V> cache, Consumer<K> consumer) {
        super(new ListenerLifecycle<>(cache, new PostPassivate<>(cache, consumer)));
    }

    public PostPassivateListener(Cache<K, V> cache, Consumer<K> consumer, Predicate<? super K> keyPredicate) {
        super(new ListenerLifecycle<>(cache, new PostPassivate<>(cache, consumer), keyPredicate));
    }

    public PostPassivateListener(Cache<K, V> cache, Consumer<K> consumer, CacheEventFilter<? super K, ? super V> filter) {
        super(new ListenerLifecycle<>(cache, new PostPassivate<>(cache, consumer), filter));
    }

    @Listener(observation = Listener.Observation.POST)
    private static class PostPassivate<K, V> {
        private final Executor executor;
        private final Consumer<K> consumer;

        @SuppressWarnings("deprecation")
        PostPassivate(Cache<K, V> cache, Consumer<K> consumer) {
            this.executor = cache.getCacheManager().getGlobalComponentRegistry().getComponent(BlockingManager.class).asExecutor(this.getClass().getName());
            this.consumer = consumer;
        }

        @CacheEntryPassivated
        public CompletionStage<Void> postPassivate(CacheEntryPassivatedEvent<K, V> event) {
            this.executor.execute(() -> this.consumer.accept(event.getKey()));
            return CompletableFutures.completedNull();
        }
    }
}
