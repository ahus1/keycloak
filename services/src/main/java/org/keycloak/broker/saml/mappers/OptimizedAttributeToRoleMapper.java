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

package org.keycloak.broker.saml.mappers;

import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.broker.provider.ConfigConstants;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;

/**
 * Optimized attribute-to-role-mapper that will skip writing to the database layer if the user is already set up with the correct roles.
 * This is a hotfix until <a href="https://github.com/keycloak/keycloak/issues/43682">#43682</a> is available.
 */
public class OptimizedAttributeToRoleMapper extends AttributeToRoleMapper {
    public static final String PROVIDER_ID = AttributeToRoleMapper.PROVIDER_ID + "-optimized";

    @Override
    public String getHelpText() {
        return super.getHelpText() + " (optimized)";
    }

    @Override
    public String getDisplayType() {
        return super.getDisplayType() + " (optimized)";
    }

    @Override
    public void updateBrokeredUser(KeycloakSession session, RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
        String roleName = mapperModel.getConfig().get(ConfigConstants.ROLE);
        RoleModel role = KeycloakModelUtils.getRoleFromString(realm, roleName);
        // If the user is already set up correctly with this role, skip
        if (role != null && this.applies(mapperModel, context) == user.hasRole(role)) {
            return;
        }
        super.updateBrokeredUser(session, realm, user, mapperModel, context);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
