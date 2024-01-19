/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service.computation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.loadflow.server.repositories.computation.AbstractComputationResultRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static org.gridsuite.loadflow.server.service.LoadFlowWorkerService.LOADFLOW_LABEL;

/**
 * @author Mathieu Deharbe <mathieu.deharbe at rte-france.com
 * @param <S> powsybl Result class specific to the computation
 * @param <R> Run context specific to a computation, including parameters
 * @param <P> powsybl and gridsuite Parameters specifics to the computation
 */
public abstract class AbstractWorkerService<S, R extends AbstractComputationRunContext<P>, P> {
    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractWorkerService.class);

    protected final Lock lockRunAndCancel = new ReentrantLock();
    protected final ObjectMapper objectMapper;
    protected final Set<UUID> runRequests = Sets.newConcurrentHashSet();
    protected final NetworkStoreService networkStoreService;
    protected final ReportService reportService;
    protected final ExecutionService executionService;
    protected final NotificationService notificationService;
    protected final AbstractComputationObserver<S, P> observer;
    protected final AbstractComputationResultRepository resultRepository;
    protected final Map<UUID, CompletableFuture<S>> futures = new ConcurrentHashMap<>();
    protected final Map<UUID, CancelContext> cancelComputationRequests = new ConcurrentHashMap<>();
    private final String computationType;

    protected AbstractWorkerService(NetworkStoreService networkStoreService,
                                    NotificationService notificationService,
                                    ReportService reportService,
                                    AbstractComputationResultRepository resultRepository,
                                    ExecutionService executionService,
                                    AbstractComputationObserver<S, P> observer,
                                    ObjectMapper objectMapper,
                                    String computationType) {
        this.networkStoreService = networkStoreService;
        this.notificationService = notificationService;
        this.reportService = reportService;
        this.executionService = executionService;
        this.observer = observer;
        this.objectMapper = objectMapper;
        this.resultRepository = resultRepository;
        this.computationType = computationType;
    }

    protected Network getNetwork(AbstractComputationRunContext<P> runContext) {
        Network network;
        try {
            UUID networkUuid = runContext.getNetworkUuid();
            String variantId = runContext.getVariantId();
            network = networkStoreService.getNetwork(networkUuid, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW);
            String variant = StringUtils.isBlank(variantId) ? VariantManagerConstants.INITIAL_VARIANT_ID : variantId;
            network.getVariantManager().setWorkingVariant(variant);
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        return network;
    }

    protected void cleanResultsAndPublishCancel(UUID resultUuid, String receiver) {
        resultRepository.delete(resultUuid);
        notificationService.publishStop(resultUuid, receiver, LOADFLOW_LABEL);
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("{} (resultUuid='{}')",
                    NotificationService.getCancelMessage(LOADFLOW_LABEL),
                    resultUuid);
        }
    }

    private void cancelAsync(CancelContext cancelContext) {
        lockRunAndCancel.lock();
        try {
            cancelComputationRequests.put(cancelContext.getResultUuid(), cancelContext);

            // find the completableFuture associated with result uuid
            CompletableFuture<S> future = futures.get(cancelContext.getResultUuid());
            if (future != null) {
                future.cancel(true);  // cancel computation in progress
            }
            cleanResultsAndPublishCancel(cancelContext.getResultUuid(), cancelContext.getReceiver());
        } finally {
            lockRunAndCancel.unlock();
        }
    }

    protected abstract AbstractResultContext<R> fromMessage(Message<String> message);

    @Bean
    public Consumer<Message<String>> consumeRun() {
        return message -> {
            AbstractResultContext<R> resultContext = fromMessage(message);
            try {
                runRequests.add(resultContext.getResultUuid());
                AtomicReference<Long> startTime = new AtomicReference<>();
                startTime.set(System.nanoTime());

                Network network = observer.observe("network.load", resultContext.getRunContext(), () -> getNetwork(resultContext.getRunContext()));

                S result = run(resultContext.getRunContext(), resultContext.getResultUuid());

                long nanoTime = System.nanoTime();
                LOGGER.info("Just run in {}s", TimeUnit.NANOSECONDS.toSeconds(nanoTime - startTime.getAndSet(nanoTime)));

                observer.observe("results.save", resultContext.getRunContext(), () -> saveResult(network, resultContext, result));

                long finalNanoTime = System.nanoTime();
                LOGGER.info("Stored in {}s", TimeUnit.NANOSECONDS.toSeconds(finalNanoTime - startTime.getAndSet(finalNanoTime)));

                if (result != null) {  // result available
                    notificationService.sendResultMessage(resultContext.getResultUuid(), resultContext.getRunContext().getReceiver());
                    LOGGER.info("{} complete (resultUuid='{}')", computationType, resultContext.getResultUuid());
                } else {  // result not available : stop computation request
                    if (cancelComputationRequests.get(resultContext.getResultUuid()) != null) {
                        cleanResultsAndPublishCancel(resultContext.getResultUuid(), cancelComputationRequests.get(resultContext.getResultUuid()).getReceiver());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                if (!(e instanceof CancellationException)) {
                    LOGGER.error(NotificationService.getFailedMessage(LOADFLOW_LABEL), e);
                    notificationService.publishFail(
                            resultContext.getResultUuid(), resultContext.getRunContext().getReceiver(),
                            e.getMessage(), resultContext.getRunContext().getUserId(), LOADFLOW_LABEL);
                    resultRepository.delete(resultContext.getResultUuid());
                }
            } finally {
                futures.remove(resultContext.getResultUuid());
                cancelComputationRequests.remove(resultContext.getResultUuid());
                runRequests.remove(resultContext.getResultUuid());
            }
        };
    }

    @Bean
    public Consumer<Message<String>> consumeCancel() {
        return message -> cancelAsync(CancelContext.fromMessage(message));
    }

    protected abstract void saveResult(Network network, AbstractResultContext<R> resultContext, S result);

    protected abstract S run(R context, UUID resultUuid) throws Exception;
}
