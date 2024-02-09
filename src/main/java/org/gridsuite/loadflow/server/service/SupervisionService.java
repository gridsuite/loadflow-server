/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import org.gridsuite.loadflow.server.repositories.LoadFlowResultRepository;
import org.springframework.stereotype.Service;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
@Service
public class SupervisionService {
    private final LoadFlowResultRepository loadFlowResultRepository;

    public SupervisionService(LoadFlowResultRepository loadFlowResultRepository) {
        this.loadFlowResultRepository = loadFlowResultRepository;
    }

    public Integer getResultsCount() {
        return (int) loadFlowResultRepository.count();
    }
}
