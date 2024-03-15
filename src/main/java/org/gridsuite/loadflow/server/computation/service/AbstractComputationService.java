/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.computation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessageHeaders;

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

    protected AbstractComputationService(NotificationService notificationService,
                                         ObjectMapper objectMapper,
                                         UuidGeneratorService uuidGeneratorService,
                                         String defaultProvider) {
        this.notificationService = Objects.requireNonNull(notificationService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.uuidGeneratorService = Objects.requireNonNull(uuidGeneratorService);
        this.defaultProvider = Objects.requireNonNull(defaultProvider);
    }

    public void stop(UUID resultUuid, String receiver) {
        notificationService.sendCancelMessage(new CancelContext(resultUuid, receiver).toMessage());
    }

    public abstract List<String> getProviders();

    public abstract UUID runAndSaveResult(R runContext);

    public abstract void deleteResult(UUID resultUuid);

    public abstract void deleteResults();

    public static String getNonNullHeader(MessageHeaders headers, String name) {
        String header = (String) headers.get(name);
        if (header == null) {
            throw new PowsyblException("Header '" + name + "' not found");
        }
        return header;
    }
}
