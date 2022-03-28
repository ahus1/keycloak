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

package org.keycloak.credential;

import org.keycloak.common.util.reflections.Types;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SingleUserCredentialManager;
import org.keycloak.models.UserModel;
import org.keycloak.models.cache.UserCache;
import org.keycloak.storage.AbstractStorageManager;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.UserStorageProviderFactory;
import org.keycloak.storage.UserStorageProviderModel;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Handling credentials for a given user.
 *
 * This serves as a wrapper to specific strategies. The wrapping code implements the logic for {@link CredentialInputUpdater}s
 * and {@link CredentialInputValidator}s. Storage specific strategies can be added like for example in
 * org.keycloak.models.map.credential.MapSingleUserCredentialManagerStrategy.
 *
 * I tried to extract the federation specific parts to the {@link DefaultSingleUserCredentialManagerStrategy} but the control
 * flow in the existing logic: if <code>model == null || !model.isEnabled()</code>, the code will directly return, while
 * the behavior of the strategy is to continue if it returns false and it will then try other providers.
 *
 * @author Alexander Schwartz
 */
public class DefaultSingleUserCredentialManager extends AbstractStorageManager<UserStorageProvider, UserStorageProviderModel> implements SingleUserCredentialManager {

    private final UserModel user;
    private final KeycloakSession session;
    private final RealmModel realm;
    private final SingleUserCredentialManagerStrategy strategy;

    public DefaultSingleUserCredentialManager(KeycloakSession session, RealmModel realm, UserModel user, SingleUserCredentialManagerStrategy strategy) {
        super(session, UserStorageProviderFactory.class, UserStorageProvider.class, UserStorageProviderModel::new, "user");
        this.user = user;
        this.session = session;
        this.realm = realm;
        this.strategy = strategy;
    }

    @Override
    public boolean isValid(List<CredentialInput> inputs) {
        if (!isValid(user)) {
            return false;
        }

        List<CredentialInput> toValidate = new LinkedList<>(inputs);

        String providerId = StorageId.isLocalStorage(user.getId()) ? user.getFederationLink() : StorageId.providerId(user.getId());
        if (providerId != null) {
            UserStorageProviderModel model = getStorageProviderModel(realm, providerId);
            if (model == null || !model.isEnabled()) return false;

            CredentialInputValidator validator = getStorageProviderInstance(model, CredentialInputValidator.class);
            if (validator != null) {
                validate(realm, user, toValidate, validator);
            }
        }

        strategy.validateCredentials(toValidate);

        getCredentialProviders(session, CredentialInputValidator.class)
                .forEach(validator -> validate(realm, user, toValidate, validator));

        return toValidate.isEmpty();
    }

    @Override
    public boolean updateCredential(CredentialInput input) {
        String providerId = StorageId.isLocalStorage(user.getId()) ? user.getFederationLink() : StorageId.providerId(user.getId());
        if (!StorageId.isLocalStorage(user.getId())) throwExceptionIfInvalidUser(user);

        if (providerId != null) {
            UserStorageProviderModel model = getStorageProviderModel(realm, providerId);
            if (model == null || !model.isEnabled()) return false;

            CredentialInputUpdater updater = getStorageProviderInstance(model, CredentialInputUpdater.class);
            if (updater != null && updater.supportsCredentialType(input.getType())) {
                if (updater.updateCredential(realm, user, input)) return true;
            }
        }

        return strategy.updateCredential(input) ||
                getCredentialProviders(session, CredentialInputUpdater.class)
                        .filter(updater -> updater.supportsCredentialType(input.getType()))
                        .anyMatch(updater -> updater.updateCredential(realm, user, input));
    }

    @Override
    public void updateStoredCredential(CredentialModel cred) {
        throwExceptionIfInvalidUser(user);
        strategy.updateStoredCredential(cred);
    }

    @Override
    public CredentialModel createStoredCredential(CredentialModel cred) {
        throwExceptionIfInvalidUser(user);
        return strategy.createStoredCredential(cred);
    }

    @Override
    public boolean removeStoredCredentialById(String id) {
        throwExceptionIfInvalidUser(user);
        return strategy.removeStoredCredentialById(id);
    }

    @Override
    public CredentialModel getStoredCredentialById(String id) {
        return strategy.getStoredCredentialById(id);
    }

    @Override
    public Stream<CredentialModel> getStoredCredentialsStream() {
        return strategy.getStoredCredentialsStream();
    }

    @Override
    public Stream<CredentialModel> getStoredCredentialsByTypeStream(String type) {
        return strategy.getStoredCredentialsByTypeStream(type);
    }

    @Override
    public CredentialModel getStoredCredentialByNameAndType(String name, String type) {
        return strategy.getStoredCredentialByNameAndType(name, type);
    }

    @Override
    public boolean moveStoredCredentialTo(String id, String newPreviousCredentialId) {
        throwExceptionIfInvalidUser(user);
        return strategy.moveStoredCredentialTo(id, newPreviousCredentialId);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isValid(UserModel user) {
        Objects.requireNonNull(user);
        return user.getServiceAccountClientLink() == null;
    }

    private void validate(RealmModel realm, UserModel user, List<CredentialInput> toValidate, CredentialInputValidator validator) {
        toValidate.removeIf(input -> validator.supportsCredentialType(input.getType()) && validator.isValid(realm, user, input));
    }

    private static <T> Stream<T> getCredentialProviders(KeycloakSession session, Class<T> type) {
        //noinspection unchecked
        return session.getKeycloakSessionFactory().getProviderFactoriesStream(CredentialProvider.class)
                .filter(f -> Types.supports(type, f, CredentialProviderFactory.class))
                .map(f -> (T) session.getProvider(CredentialProvider.class, f.getId()));
    }

    private void throwExceptionIfInvalidUser(UserModel user) {
        if (!isValid(user)) {
            throw new RuntimeException("You can not manage credentials for this user");
        }
    }

}

