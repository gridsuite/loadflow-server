/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.mergingview.MergingView;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.loadflow.server.dto.LoadFlowStatus;
import org.gridsuite.loadflow.server.repositories.LoadFlowResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.gridsuite.loadflow.server.service.NotificationService.FAIL_MESSAGE;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@Service
public class LoadFlowWorkerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadFlowWorkerService.class);
    private static final String LOAD_FLOW_TYPE_REPORT = "LoadFlow";

    private Lock lockRunAndCancelLF = new ReentrantLock();

    private ObjectMapper objectMapper;

    private Set<UUID> runRequests = Sets.newConcurrentHashSet();
    private NetworkStoreService networkStoreService;
    private ReportService reportService;

    NotificationService notificationService;

    private LoadFlowResultRepository resultRepository;

    public LoadFlowWorkerService(NetworkStoreService networkStoreService, NotificationService notificationService, ReportService reportService,
                                 LoadFlowResultRepository resultRepository, ObjectMapper objectMapper) {
        this.networkStoreService = networkStoreService;
        this.notificationService = notificationService;
        this.reportService = reportService;
        this.resultRepository = resultRepository;
        this.objectMapper = objectMapper;
    }

    private Map<UUID, CompletableFuture<LoadFlowResult>> futures = new ConcurrentHashMap<>();

    private Map<UUID, LoadFlowCancelContext> cancelComputationRequests = new ConcurrentHashMap<>();

    private Network getNetwork(UUID networkUuid, String variantId) {
        Network network;
        try {
            network = networkStoreService.getNetwork(networkUuid, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW);
            String variant = StringUtils.isBlank(variantId) ? VariantManagerConstants.INITIAL_VARIANT_ID : variantId;
            network.getVariantManager().setWorkingVariant(variant);
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        return network;
    }

    private Network getNetwork(UUID networkUuid, List<UUID> otherNetworkUuids, String variantId) {
        Network network = getNetwork(networkUuid, variantId);
        if (otherNetworkUuids.isEmpty()) {
            return network;
        } else {
            List<Network> otherNetworks = otherNetworkUuids.stream().map(uuid -> getNetwork(uuid, variantId)).collect(Collectors.toList());
            List<Network> networks = new ArrayList<>();
            networks.add(network);
            networks.addAll(otherNetworks);
            MergingView mergingView = MergingView.create("merge", "iidm");
            mergingView.merge(networks.toArray(new Network[0]));
            return mergingView;
        }
    }

    private CompletableFuture<LoadFlowResult> runLoadFlowAsync(
            Network network,
            String variantId,
            String provider,
            LoadFlowParameters params,
            Reporter reporter,
            UUID resultUuid) {
        lockRunAndCancelLF.lock();
        try {
            if (resultUuid != null && cancelComputationRequests.get(resultUuid) != null) {
                return null;
            }
            LoadFlow.Runner runner = LoadFlow.find(provider);
            CompletableFuture<LoadFlowResult> future = runner.runAsync(network, variantId != null ? variantId : VariantManagerConstants.INITIAL_VARIANT_ID, LocalComputationManager.getDefault(), params, reporter);
            if (resultUuid != null) {
                futures.put(resultUuid, future);
            }
            return future;
        } finally {
            lockRunAndCancelLF.unlock();
        }
    }

    public LoadFlowResult run(LoadFlowRunContext context, UUID resultUuid) throws ExecutionException, InterruptedException {
        LoadFlowParameters params = context.getParameters();
        LOGGER.info("Run loadFlow...");
        Network network = getNetwork(context.getNetworkUuid(), context.getOtherNetworksUuids(), context.getVariantId());
        String provider = context.getProvider();

        Reporter rootReporter = Reporter.NO_OP;
        Reporter reporter = Reporter.NO_OP;
        if (context.getReportContext() != null) {
            String rootReporterId = context.getReportContext().getReportName() == null ? LOAD_FLOW_TYPE_REPORT : context.getReportContext().getReportName() + "@" + LOAD_FLOW_TYPE_REPORT;
            rootReporter = new ReporterModel(rootReporterId, rootReporterId);
            reporter = rootReporter.createSubReporter(LOAD_FLOW_TYPE_REPORT, String.format(LOAD_FLOW_TYPE_REPORT + " (%s)", provider), "providerToUse", provider);
        }

        CompletableFuture<LoadFlowResult> future = runLoadFlowAsync(network, context.getVariantId(), context.getProvider(), params, reporter, resultUuid);

        LoadFlowResult result = future == null ? null : future.get();
        if (result.isOk()) {
            // flush each network in the network store
            networkStoreService.flush(network);
        }
        if (context.getReportContext().getReportId() != null) {
            reportService.sendReport(context.getReportContext().getReportId(), rootReporter);
        }
        return result;
    }

    private void cleanLoadFlowResultsAndPublishCancel(UUID resultUuid, String receiver) {
        resultRepository.delete(resultUuid);
        notificationService.publishStop(resultUuid, receiver);
    }

    private void cancelLoadFlowAsync(LoadFlowCancelContext cancelContext) {
        lockRunAndCancelLF.lock();
        try {
            cancelComputationRequests.put(cancelContext.getResultUuid(), cancelContext);

            // find the completableFuture associated with result uuid
            CompletableFuture<LoadFlowResult> future = futures.get(cancelContext.getResultUuid());
            if (future != null) {
                future.cancel(true);  // cancel computation in progress
            }
            cleanLoadFlowResultsAndPublishCancel(cancelContext.getResultUuid(), cancelContext.getReceiver());
        } finally {
            lockRunAndCancelLF.unlock();
        }
    }

    @Bean
    public Consumer<Message<String>> consumeRun() {
        return message -> {
            LoadFlowResultContext resultContext = LoadFlowResultContext.fromMessage(message, objectMapper);
            try {
                runRequests.add(resultContext.getResultUuid());
                AtomicReference<Long> startTime = new AtomicReference<>();

                startTime.set(System.nanoTime());
                LoadFlowResult result = run(resultContext.getRunContext(), resultContext.getResultUuid());
                long nanoTime = System.nanoTime();
                LOGGER.info("Just run in {}s", TimeUnit.NANOSECONDS.toSeconds(nanoTime - startTime.getAndSet(nanoTime)));

                resultRepository.insert(resultContext.getResultUuid(), result);
                resultRepository.insertStatus(List.of(resultContext.getResultUuid()), LoadFlowStatus.COMPLETED);
                long finalNanoTime = System.nanoTime();
                LOGGER.info("Stored in {}s", TimeUnit.NANOSECONDS.toSeconds(finalNanoTime - startTime.getAndSet(finalNanoTime)));

                if (result != null) {  // result available
                    notificationService.sendResultMessage(resultContext.getResultUuid(), resultContext.getRunContext().getReceiver());
                    LOGGER.info("LoadFlow complete (resultUuid='{}')", resultContext.getResultUuid());
                } else {  // result not available : stop computation request
                    if (cancelComputationRequests.get(resultContext.getResultUuid()) != null) {
                        cleanLoadFlowResultsAndPublishCancel(resultContext.getResultUuid(), cancelComputationRequests.get(resultContext.getResultUuid()).getReceiver());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOGGER.error(FAIL_MESSAGE, e);
                if (!(e instanceof CancellationException)) {
                    notificationService.publishFail(resultContext.getResultUuid(), resultContext.getRunContext().getReceiver(), e.getMessage(), resultContext.getRunContext().getUserId());
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
        return message -> cancelLoadFlowAsync(LoadFlowCancelContext.fromMessage(message));
    }

}
