/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.appengine.task.appcfg

import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile

/**
 * Google App Engine task downloading application logs from the server.
 *
 * @see <a href="http://code.google.com/appengine/docs/java/tools/uploadinganapp.html#Downloading_Logs">Documentation</a>
 * @author Benjamin Muschko
 */
class DownloadLogsTask extends AppConfigTaskTemplate {
    static final String COMMAND = 'request_logs'
    Integer numDays
    Integer severity
    Boolean append
    Boolean includeAll
    @Input File outputFile

    @Override
    void validateConfiguration() {
        super.validateConfiguration()

        if(getSeverity() != null && (getSeverity() < 0 || getSeverity() > 4)) {
            throw new InvalidUserDataException("Invalid log level: ${getSeverity()}. Valid values are 4 for CRITICAL, 3 for ERROR, 2 for WARNING, 1 for INFO, 0 for DEBUG.")
        }
        else {
            logger.info "Pulling messages with minimum log level = ${getSeverity()}"
        }
    }

    @Override
    String startLogMessage() {
        'Starting downloading logs...'
    }

    @Override
    String errorLogMessage() {
        'An error occurred downloading logs from App Engine.'
    }

    @Override
    String finishLogMessage() {
        'Finished downloading logs.'
    }

    @Override
    List getParams() {
        def params = []

        if(getNumDays() != null) {
            params << "--num_days=${getNumDays()}"
        }

        if(getSeverity() != null) {
            params << "--severity=${getSeverity()}"
        }

        if(getAppend()) {
            params << '--append'
        }

        if(getIncludeAll()) {
            params << '--include_all'
        }

        params << COMMAND
        params << getWorkingDirectory().canonicalPath
        params << getOutputFile().canonicalFile
        params
    }
}
