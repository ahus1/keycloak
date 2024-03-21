package org.keycloak.cluster.infinispan.remote;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.annotation.ClientCacheEntryCreated;
import org.infinispan.client.hotrod.annotation.ClientCacheEntryModified;
import org.infinispan.client.hotrod.annotation.ClientCacheEntryRemoved;
import org.infinispan.client.hotrod.annotation.ClientListener;
import org.infinispan.client.hotrod.event.ClientCacheEntryCreatedEvent;
import org.infinispan.client.hotrod.event.ClientCacheEntryModifiedEvent;
import org.infinispan.client.hotrod.event.ClientCacheEntryRemovedEvent;
import org.infinispan.client.hotrod.exceptions.HotRodClientException;
import org.jboss.logging.Logger;
import org.keycloak.cluster.ClusterEvent;
import org.keycloak.cluster.ClusterListener;
import org.keycloak.cluster.ClusterProvider.DCNotify;
import org.keycloak.cluster.infinispan.TaskCallback;
import org.keycloak.cluster.infinispan.WrapperClusterEvent;
import org.keycloak.common.util.ConcurrentMultivaluedHashMap;
import org.keycloak.common.util.Retry;
import org.keycloak.connections.infinispan.TopologyInfo;

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.keycloak.cluster.infinispan.InfinispanClusterProvider.TASK_KEY_PREFIX;

@ClientListener
public class RemoteInfinispanNotificationManager {

    private static final Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());

    private final ConcurrentMap<String, TaskCallback> taskCallbacks = new ConcurrentHashMap<>();
    private final ConcurrentMultivaluedHashMap<String, ClusterListener> listeners = new ConcurrentMultivaluedHashMap<>();
    private final Executor executor;
    private final RemoteCache<String, WrapperClusterEvent> workCache;
    private final TopologyInfo topologyInfo;

    public RemoteInfinispanNotificationManager(Executor executor, RemoteCache<String, WrapperClusterEvent> workCache, TopologyInfo topologyInfo) {
        this.executor = executor;
        this.workCache = workCache;
        this.topologyInfo = topologyInfo;
    }

    public void addClientListener() {
        workCache.addClientListener(this);
    }

    public void removeClientListener() {
        // workaround because providers are independent and close() can be invoked in any order.
        if (workCache.getRemoteCacheContainer().isStarted()) {
            workCache.removeClientListener(this);
        }
    }

    public void registerListener(String taskKey, ClusterListener task) {
        listeners.add(taskKey, task);
    }

    public TaskCallback registerTaskCallback(String taskKey, TaskCallback callback) {
        var existing = taskCallbacks.putIfAbsent(taskKey, callback);
        return existing != null ? existing : callback;
    }

    public void notify(String taskKey, ClusterEvent event, boolean ignoreSender, DCNotify dcNotify) {
        WrapperClusterEvent wrappedEvent = new WrapperClusterEvent();
        wrappedEvent.setEventKey(taskKey);
        wrappedEvent.setDelegateEvent(event);
        wrappedEvent.setIgnoreSender(ignoreSender);
        wrappedEvent.setIgnoreSenderSite(dcNotify == DCNotify.ALL_BUT_LOCAL_DC);
        wrappedEvent.setSender(topologyInfo.getMyNodeName());
        wrappedEvent.setSenderSite(topologyInfo.getMySiteName());

        String eventKey = UUID.randomUUID().toString();

        if (logger.isTraceEnabled()) {
            logger.tracef("Sending event with key %s: %s", eventKey, event);
        }

        //TODO [pruivo] how to handle DCNotify.LOCAL_DC_ONLY with remote caches?? change the wrapper event?

        Retry.executeWithBackoff((int iteration) -> {
            try {
                workCache.put(eventKey, wrappedEvent, 120, TimeUnit.SECONDS);
            } catch (HotRodClientException re) {
                if (logger.isDebugEnabled()) {
                    logger.debugf(re, "Failed sending notification to remote cache '%s'. Key: '%s', iteration '%s'. Will try to retry the task",
                            workCache.getName(), eventKey, iteration);
                }

                // Rethrow the exception. Retry will take care of handle the exception and eventually retry the operation.
                throw re;
            }

        }, 10, 10);

    }

    public String getMyNodeName() {
        return topologyInfo.getMyNodeName();
    }

    @ClientCacheEntryCreated
    public void created(ClientCacheEntryCreatedEvent<String> event) {
        String key = event.getKey();
        hotrodEventReceived(key);
    }


    @ClientCacheEntryModified
    public void updated(ClientCacheEntryModifiedEvent<String> event) {
        String key = event.getKey();
        hotrodEventReceived(key);
    }


    @ClientCacheEntryRemoved
    public void removed(ClientCacheEntryRemovedEvent<String> event) {
        String key = event.getKey();
        taskFinished(key);
    }

    private void hotrodEventReceived(String key) {
        // TODO [pruivo] cache event converter may work here with protostream
        workCache.getAsync(key).thenAcceptAsync(value -> eventReceived(key, value), executor);
    }

    private void eventReceived(String key, Serializable obj) {
        if (!(obj instanceof WrapperClusterEvent event)) {
            // Items with the TASK_KEY_PREFIX might be gone fast once the locking is complete, therefore, don't log them.
            // It is still good to have the warning in case of real events return null because they have been, for example, expired
            if (obj == null && !key.startsWith(TASK_KEY_PREFIX)) {
                logger.warnf("Event object wasn't available in remote cache after event was received. Event key: %s", key);
            }
            return;
        }

        if (event.isIgnoreSender() && Objects.equals(topologyInfo.getMyNodeName(), event.getSender())) {
            return;
        }

        if (event.isIgnoreSenderSite() && Objects.equals(topologyInfo.getMySiteName(), event.getSenderSite())) {
            return;
        }

        if (logger.isTraceEnabled()) {
            logger.tracef("Received event: %s", event);
        }

        listeners.getOrDefault(event.getEventKey(), Collections.emptyList()).forEach(event);
    }

    private void taskFinished(String taskKey) {
        TaskCallback callback = taskCallbacks.remove(taskKey);
        if (callback == null) {
            return;
        }
        if (logger.isDebugEnabled()) {
            logger.debugf("Finished task '%s' with '%b'", taskKey, true);
        }
        callback.setSuccess(true);
        callback.getTaskCompletedLatch().countDown();
    }
}
