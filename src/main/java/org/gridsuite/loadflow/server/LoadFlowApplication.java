/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import com.fasterxml.jackson.databind.Module;
import com.powsybl.loadflow.json.LoadFlowResultJsonModule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@SpringBootApplication
public class LoadFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoadFlowApplication.class, args);
    }

    @Bean
    public Module createLoadFlowResultModule() {
        return new LoadFlowResultJsonModule();
    }
}
