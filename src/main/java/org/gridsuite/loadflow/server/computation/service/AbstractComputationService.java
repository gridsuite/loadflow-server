/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.computation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.gridsuite.loadflow.server.computation.repositories.ComputationResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Mathieu Deharbe <mathieu.deharbe at rte-france.com
 * @param <R> run context specific to a computation, including parameters
 */
public abstract class AbstractComputationService<R> {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractComputationService.class);

    protected ObjectMapper objectMapper;
    protected NotificationService notificationService;
    @Getter
    protected String defaultProvider;

    protected UuidGeneratorService uuidGeneratorService;
    protected ComputationResultRepository resultRepository;

    protected AbstractComputationService(NotificationService notificationService, ComputationResultRepository resultRepository,
                                         ObjectMapper objectMapper, UuidGeneratorService uuidGeneratorService,
                                         String defaultProvider) {
        this.notificationService = Objects.requireNonNull(notificationService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.uuidGeneratorService = Objects.requireNonNull(uuidGeneratorService);
        this.defaultProvider = Objects.requireNonNull(defaultProvider);
        this.resultRepository = Objects.requireNonNull(resultRepository);
    }

    public void stop(UUID resultUuid, String receiver) {
        notificationService.sendCancelMessage(new CancelContext(resultUuid, receiver).toMessage());
    }

    public abstract List<String> getProviders();

    public abstract UUID runAndSaveResult(R runContext, UUID parametersUuid);

    public void deleteResult(UUID resultUuid) {
        resultRepository.delete(resultUuid);
    }

    public void deleteResults(List<UUID> resultUuids) {
        if (resultUuids != null && !resultUuids.isEmpty()) {
            resultUuids.forEach(resultRepository::delete);
        } else {
            deleteResults();
        }
    }

    public void deleteResults() {
        resultRepository.deleteAll();
    }
}
