/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import com.powsybl.network.store.client.NetworkStoreService;
import org.gridsuite.computation.error.RestResponseEntityExceptionHandler;
import org.gridsuite.computation.service.NotificationService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@SpringBootApplication(scanBasePackageClasses = { LoadFlowApplication.class, NetworkStoreService.class, NotificationService.class, RestResponseEntityExceptionHandler.class })
public class LoadFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(LoadFlowApplication.class, args);
    }
}
