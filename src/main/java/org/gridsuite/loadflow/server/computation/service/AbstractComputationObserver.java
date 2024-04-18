/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.computation.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.NonNull;

/**
 * @author Mathieu Deharbe <mathieu.deharbe at rte-france.com
 * @param <R> powsybl Result class specific to the computation
 * @param <P> powsybl and gridsuite parameters specifics to the computation
 */
public abstract class AbstractComputationObserver<R, P> {
    protected static final String OBSERVATION_PREFIX = "app.computation.";
    protected static final String PROVIDER_TAG_NAME = "provider";
    protected static final String TYPE_TAG_NAME = "type";
    protected static final String STATUS_TAG_NAME = "status";
    protected static final String COMPUTATION_COUNTER_NAME = OBSERVATION_PREFIX + "count";
    protected final ObservationRegistry observationRegistry;
    protected final MeterRegistry meterRegistry;

    protected AbstractComputationObserver(@NonNull ObservationRegistry observationRegistry, @NonNull MeterRegistry meterRegistry) {
        this.observationRegistry = observationRegistry;
        this.meterRegistry = meterRegistry;
    }

    protected abstract String getComputationType();

    protected Observation createObservation(String name, AbstractComputationRunContext<P> runContext) {
        return Observation.createNotStarted(OBSERVATION_PREFIX + name, observationRegistry)
                .lowCardinalityKeyValue(PROVIDER_TAG_NAME, runContext.getProvider())
                .lowCardinalityKeyValue(TYPE_TAG_NAME, getComputationType());
    }

    public <E extends Throwable> void observe(String name, AbstractComputationRunContext<P> runContext, Observation.CheckedRunnable<E> callable) throws E {
        createObservation(name, runContext).observeChecked(callable);
    }

    public <T, E extends Throwable> T observe(String name, AbstractComputationRunContext<P> runContext, Observation.CheckedCallable<T, E> callable) throws E {
        return createObservation(name, runContext).observeChecked(callable);
    }

    public <T extends R, E extends Throwable> T observeRun(
            String name, AbstractComputationRunContext<P> runContext, Observation.CheckedCallable<T, E> callable) throws E {
        T result = createObservation(name, runContext).observeChecked(callable);
        incrementCount(runContext, result);
        return result;
    }

    private void incrementCount(AbstractComputationRunContext<P> runContext, R result) {
        Counter.builder(COMPUTATION_COUNTER_NAME)
                .tag(PROVIDER_TAG_NAME, runContext.getProvider())
                .tag(TYPE_TAG_NAME, getComputationType())
                .tag(STATUS_TAG_NAME, getResultStatus(result))
                .register(meterRegistry)
                .increment();
    }

    protected abstract String getResultStatus(R res);
}
