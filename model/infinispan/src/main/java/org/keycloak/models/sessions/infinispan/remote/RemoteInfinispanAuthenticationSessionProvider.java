package org.keycloak.models.sessions.infinispan.remote;

import org.keycloak.cluster.ClusterProvider;
import org.keycloak.common.util.Time;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.cache.infinispan.events.AuthenticationSessionAuthNoteUpdateEvent;
import org.keycloak.models.sessions.infinispan.InfinispanAuthenticationSessionProviderFactory;
import org.keycloak.models.sessions.infinispan.RootAuthenticationSessionAdapter;
import org.keycloak.models.sessions.infinispan.SessionEntityUpdater;
import org.keycloak.models.sessions.infinispan.entities.RootAuthenticationSessionEntity;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.SessionExpiration;
import org.keycloak.sessions.AuthenticationSessionCompoundId;
import org.keycloak.sessions.AuthenticationSessionProvider;
import org.keycloak.sessions.RootAuthenticationSessionModel;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class RemoteInfinispanAuthenticationSessionProvider implements AuthenticationSessionProvider {

    private final KeycloakSession session;
    private final RemoteInfinispanKeycloakTransaction<String, RootAuthenticationSessionEntity> transaction;
    private final int authSessionsLimit;

    public RemoteInfinispanAuthenticationSessionProvider(KeycloakSession session, RemoteInfinispanAuthenticationSessionProviderFactory factory) {
        this.session = Objects.requireNonNull(session);
        authSessionsLimit = Objects.requireNonNull(factory).getAuthSessionsLimit();
        transaction = new RemoteInfinispanKeycloakTransaction<>(factory.getCache());
        session.getTransactionManager().enlistAfterCompletion(transaction);
    }

    @Override
    public void close() {

    }

    @Override
    public RootAuthenticationSessionModel createRootAuthenticationSession(RealmModel realm) {
        return createRootAuthenticationSession(realm, KeycloakModelUtils.generateId());
    }

    @Override
    public RootAuthenticationSessionModel createRootAuthenticationSession(RealmModel realm, String id) {
        RootAuthenticationSessionEntity entity = new RootAuthenticationSessionEntity();
        entity.setId(id);
        entity.setRealmId(realm.getId());
        entity.setTimestamp(Time.currentTime());

        int expirationSeconds = SessionExpiration.getAuthSessionLifespan(realm);
        transaction.put(id, entity, expirationSeconds, TimeUnit.SECONDS);

        return wrap(realm, entity);
    }

    @Override
    public RootAuthenticationSessionModel getRootAuthenticationSession(RealmModel realm, String authenticationSessionId) {
        return wrap(realm, transaction.get(authenticationSessionId));
    }

    @Override
    public void removeRootAuthenticationSession(RealmModel realm, RootAuthenticationSessionModel authenticationSession) {
        transaction.remove(authenticationSession.getId());
    }

    @Override
    public void removeAllExpired() {
        // Rely on expiration of cache entries provided by infinispan. Nothing needed here.
    }

    @Override
    public void removeExpired(RealmModel realm) {
        // Rely on expiration of cache entries provided by infinispan. Nothing needed here.
    }

    @Override
    public void onRealmRemoved(RealmModel realm) {
        // TODO [pruivo] [optimization] with protostream, use delete by query: DELETE FROM ...
        var cache = transaction.getCache();
        try (var iterator = cache.retrieveEntries(null, 256)) {
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (realm.getId().equals(((RootAuthenticationSessionEntity) entry.getValue()).getRealmId())) {
                    cache.removeAsync(entry.getKey());
                }
            }
        }
    }

    @Override
    public void onClientRemoved(RealmModel realm, ClientModel client) {
        // No update anything on clientRemove for now. AuthenticationSessions of removed client will be handled at runtime if needed.
    }

    @SuppressWarnings("deprecation")
    @Override
    public void updateNonlocalSessionAuthNotes(AuthenticationSessionCompoundId compoundId, Map<String, String> authNotesFragment) {
        if (compoundId == null) {
            return;
        }

        session.getProvider(ClusterProvider.class).notify(
                InfinispanAuthenticationSessionProviderFactory.AUTHENTICATION_SESSION_EVENTS,
                AuthenticationSessionAuthNoteUpdateEvent.create(compoundId.getRootSessionId(), compoundId.getTabId(), compoundId.getClientUUID(), authNotesFragment),
                true,
                ClusterProvider.DCNotify.ALL_BUT_LOCAL_DC
        );
    }

    private RootAuthenticationSessionAdapter wrap(RealmModel realm, RootAuthenticationSessionEntity entity) {
        return entity == null ? null : new RootAuthenticationSessionAdapter(session, new RootAuthenticationSessionUpdater(realm, entity, transaction), realm, authSessionsLimit);
    }

    private record RootAuthenticationSessionUpdater(RealmModel realm, RootAuthenticationSessionEntity entity,
                                                    RemoteInfinispanKeycloakTransaction<String, RootAuthenticationSessionEntity> transaction
    ) implements SessionEntityUpdater<RootAuthenticationSessionEntity> {

        @Override
        public RootAuthenticationSessionEntity getEntity() {
            return entity;
        }

        @Override
        public void onEntityUpdated() {
            int expirationSeconds = entity.getTimestamp() - Time.currentTime() + SessionExpiration.getAuthSessionLifespan(realm);
            transaction.replace(entity.getId(), entity, expirationSeconds, TimeUnit.SECONDS);
        }

        @Override
        public void onEntityRemoved() {
            transaction.remove(entity.getId());
        }
    }
}
