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

package org.keycloak.models;

import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialModel;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Validates and manages the credentials of a known user.
 */
public interface SingleUserCredentialManager {

    /**
     * Validate a list of credentials.
     *
     * @return <code>true</code> if inputs are valid
     */
    boolean isValid(List<CredentialInput> inputs);

    /**
     * Validate a list of credentials.
     *
     * @return <code>true</code> if inputs are valid
     */
    default boolean isValid(CredentialInput... inputs) {
        return isValid(Arrays.asList(inputs));
    }

    /**
     * Updates a credential of the user with the inputs provided by the user.
     * @return <code>true</code> if credentials have been updated successfully
     */
    boolean updateCredential(CredentialInput input);

    /**
     * Updates a credential of the user with an updated {@link CredentialModel}.
     * Usually called by a {@link org.keycloak.credential.CredentialProvider}.
     */
    void updateStoredCredential(CredentialModel cred);

    /**
     * Updates a credential of the user with an updated {@link CredentialModel}.
     * Usually called by a {@link org.keycloak.credential.CredentialProvider}.
     */
    CredentialModel createStoredCredential(CredentialModel cred);

    /**
     * Updates a credential of the user with an updated {@link CredentialModel}.
     * Usually called by a {@link org.keycloak.credential.CredentialProvider}, or from the account management
     * when a user removes, for example, an OTP token.
     */
    boolean removeStoredCredentialById(String id);

    /**
     * Read a stored credential.
     */
    CredentialModel getStoredCredentialById(String id);

    /**
     * Read stored credentials as a stream.
     */
    Stream<CredentialModel> getStoredCredentialsStream();

    /**
     * Read stored credentials by type as a stream.
     */
    Stream<CredentialModel> getStoredCredentialsByTypeStream(String type);

    CredentialModel getStoredCredentialByNameAndType(String name, String type);

    /**
     * Re-order the stored credentials.
     */
    boolean moveStoredCredentialTo(String id, String newPreviousCredentialId);

    /**
     * Update the label for a stored credentials.
     */
    void updateCredentialLabel(String credentialId, String userLabel);

    /**
     * Disable a credential by type.
     */
    void disableCredentialType(String credentialType);

    /**
     * List the credentials that can be disabled, for example, to show the list to the user or an admin.
     * @return stream with credential types that can be disabled
     */
    Stream<String> getDisableableCredentialTypesStream();

    /**
     * Check if the credential type is configured for this user.
     * @param type credential type to check
     * @return <code>true</code> if the credential type has been 
     */
    boolean isConfiguredFor(String type);

    // TODO: not needed for new store? -> no, will be removed without replacement
    @Deprecated
    boolean isConfiguredLocally(String type);

    // TODO: not needed for new store? -> no, will be removed without replacement
    Stream<String> getConfiguredUserStorageCredentialTypesStream();

    // TODO: not needed for new store? -> no, will be removed without replacement
    CredentialModel createCredentialThroughProvider(CredentialModel model);
}
