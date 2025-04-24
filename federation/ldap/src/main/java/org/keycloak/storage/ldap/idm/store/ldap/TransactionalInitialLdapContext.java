/*
 * Copyright 2025 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.storage.ldap.idm.store.ldap;

import org.keycloak.models.AbstractKeycloakTransaction;
import org.keycloak.models.KeycloakSession;

import javax.naming.NamingException;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import java.util.Hashtable;

/**
 * A {@link InitialLdapContext} that enlists a after completion transaction to release any LDAP resource acquired
 * during the {@link KeycloakSession} lifetime.
 */
public final class TransactionalInitialLdapContext extends InitialLdapContext {

    public TransactionalInitialLdapContext(KeycloakSession session, Hashtable<?, ?> environment, Control[] connCtls) throws NamingException {
        super(environment, connCtls);
        session.getTransactionManager().enlistAfterCompletion(new AbstractKeycloakTransaction() {
            @Override
            protected void commitImpl() {
                try {
                    close();
                } catch (NamingException e) {
                    failedToCloseLdapContext(e);
                }
            }

            @Override
            protected void rollbackImpl() {
                try {
                    close();
                } catch (NamingException e) {
                    failedToCloseLdapContext(e);
                }
            }

            private void failedToCloseLdapContext(NamingException e) {
                throw new RuntimeException("Failed to close LDAP context", e);
            }
        });
    }
}
