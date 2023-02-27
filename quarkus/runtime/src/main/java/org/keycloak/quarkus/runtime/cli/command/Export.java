/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.quarkus.runtime.cli.command;

import static org.keycloak.exportimport.ExportImportConfig.ACTION_EXPORT;
import static org.keycloak.exportimport.ExportImportConfig.USERS_EXPORT_STRATEGY;
import static org.keycloak.exportimport.ExportImportConfig.USERS_PER_FILE;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = Export.NAME,
        header = "Export data from realms to a file or directory.",
        description = "%nExport data from realms to a file or directory.")
public final class Export extends AbstractExportImportCommand implements Runnable {

    public static final String NAME = "export";

    @Option(names = "--users",
            arity = "1",
            description = "Set how users should be exported. Possible values are: skip, realm_file, same_file, different_files.",
            paramLabel = "<strategy>",
            defaultValue = "different_files")
    String users;

    @Option(names = "--users-per-file",
            arity = "1",
            description = "Set the number of users per file. It’s used only if --users=different_files.",
            paramLabel = "<number>",
            defaultValue = "50")
    Integer usersPerFile;

    @Option(names = "--realm",
            arity = "1",
            description = "Set the name of the realm to export. If not set, all realms are going to be exported.",
            paramLabel = "<realm>")
    String realm;

    public Export() {
        super(ACTION_EXPORT);
    }

    @Override
    protected void doBeforeRun() {
        System.setProperty(USERS_EXPORT_STRATEGY, users.toUpperCase());

        if (usersPerFile != null) {
            System.setProperty(USERS_PER_FILE, usersPerFile.toString());
        }
    }
}
