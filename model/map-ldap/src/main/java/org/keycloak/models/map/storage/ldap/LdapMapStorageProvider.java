/*
 * Copyright 2022. Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.models.map.storage.ldap;

import org.keycloak.models.UserModel;
import org.keycloak.models.map.common.AbstractEntity;
import org.keycloak.models.map.storage.MapKeycloakTransaction;
import org.keycloak.models.map.storage.MapStorage;
import org.keycloak.models.map.storage.MapStorageProvider;
import org.keycloak.models.map.storage.MapStorageProviderFactory.Flag;
import org.keycloak.models.map.storage.ldap.user.LdapUserMapKeycloakTransaction;
import org.keycloak.models.map.user.MapUserEntity;

public class LdapMapStorageProvider implements MapStorageProvider {

    private final LdapMapStorageProviderFactory factory;
    private final String sessionTxPrefix;
    @Deprecated
    private final MapStorageProvider delegate;

    public LdapMapStorageProvider(LdapMapStorageProviderFactory factory, String sessionTxPrefix, MapStorageProvider delegate) {
        this.factory = factory;
        this.sessionTxPrefix = sessionTxPrefix;
        this.delegate = delegate;
    }

    @Override
    public void close() {
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V extends AbstractEntity, M> MapStorage<V, M> getStorage(Class<M> modelType, Flag... flags) {
        return session -> {
            MapKeycloakTransaction<V, M> sessionTx = session.getAttribute(sessionTxPrefix + modelType.hashCode(), MapKeycloakTransaction.class);
            if (sessionTx == null) {
                sessionTx = factory.createTransaction(session, modelType);
                session.setAttribute(sessionTxPrefix + modelType.hashCode(), sessionTx);

                if (modelType == UserModel.class) {
                    MapStorage<V, M> delegateStorage = delegate.getStorage(modelType, flags);
                    MapKeycloakTransaction<V, M> delegateTransaction = delegateStorage.createTransaction(session);
                    ((LdapUserMapKeycloakTransaction) sessionTx).setDelegate((MapKeycloakTransaction<MapUserEntity, UserModel>) delegateTransaction);
                }

            }
            return sessionTx;
        };
    }

}
