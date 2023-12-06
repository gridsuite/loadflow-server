/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.NonNull;
import org.springframework.stereotype.Service;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@Service
public class LoadflowObserver {

    private final ObservationRegistry observationRegistry;

    private static final String OBSERVATION_PREFIX = "app.loadflow.";
    private static final String USER_TAG_NAME = "user";
    private static final String PROVIDER_TAG_NAME = "provider";

    public LoadflowObserver(@NonNull ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    public <E extends Throwable> void observe(String name, LoadFlowRunContext runContext, Observation.CheckedRunnable<E> callable) throws E {
        createLoadflowObservation(name, runContext).observeChecked(callable);
    }

    public <T, E extends Throwable> T observe(String name, LoadFlowRunContext runContext, Observation.CheckedCallable<T, E> callable) throws E {
        return createLoadflowObservation(name, runContext).observeChecked(callable);
    }

    private Observation createLoadflowObservation(String name, LoadFlowRunContext runContext) {
        return Observation.createNotStarted(OBSERVATION_PREFIX + name, observationRegistry)
                .lowCardinalityKeyValue(USER_TAG_NAME, runContext.getUserId())
                .lowCardinalityKeyValue(PROVIDER_TAG_NAME, runContext.getProvider());
    }
}
