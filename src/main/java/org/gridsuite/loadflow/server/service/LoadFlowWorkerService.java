/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@Service
public class LoadFlowWorkerService {
    private static final String LOAD_FLOW_TYPE_REPORT = "LoadFlow";

    private Lock lockRunAndCancelLF = new ReentrantLock();

    @Autowired
    private NetworkStoreService networkStoreService;
    @Autowired
    private ReportService reportService;

    private Mono<Network> getNetwork(UUID networkUuid) {
        // FIXME to re-implement when network store service will be reactive
        return Mono.fromCallable(() -> {
            try {
                return networkStoreService.getNetwork(networkUuid, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW);
            } catch (PowsyblException e) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
            }
        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Network> getNetwork(UUID networkUuid, List<UUID> otherNetworkUuids) {
        Mono<Network> network = getNetwork(networkUuid);
        if (otherNetworkUuids.isEmpty()) {
            return network;
        } else {
            Mono<List<Network>> otherNetworks = Flux.fromIterable(otherNetworkUuids)
                    .flatMap(this::getNetwork)
                    .collectList();
            return Mono.zip(network, otherNetworks)
                    .map(t -> {
                        // creation of the merging view
                        List<Network> networks = new ArrayList<>();
                        networks.add(t.getT1());
                        networks.addAll(t.getT2());
                        MergingView mergingView = MergingView.create("merge", "iidm");
                        mergingView.merge(networks.toArray(new Network[0]));
                        return mergingView;
                    });
        }
    }

    private CompletableFuture<LoadFlowResult> runASAsync(
            Network network,
            String variantId,
            LoadFlowParameters params,
            LoadFlow.Runner loadFLoRunner,
            Reporter reporter) {
        lockRunAndCancelLF.lock();
        try {
            return loadFLoRunner.runAsync(network, variantId != null ? variantId : VariantManagerConstants.INITIAL_VARIANT_ID, LocalComputationManager.getDefault(), params, reporter);
        } finally {
            lockRunAndCancelLF.unlock();
        }
    }

    public Mono<LoadFlowResult> run(LoadFlowRunContext context) {
        LoadFlowParameters params = context.getParameters();
        Mono<Network> network = getNetwork(context.getNetworkUuid(), context.getOtherNetworkUuids());

        return network
                .flatMap(tuple -> {
                    LoadFlow.Runner runner = LoadFlow.find(context.getProvider());

                    Reporter rootReporter = Reporter.NO_OP;
                    Reporter reporter = Reporter.NO_OP;
                    if (context.getReportContext() != null) {
                        String rootReporterId = context.getReportContext().getReportName() == null ? LOAD_FLOW_TYPE_REPORT : context.getReportContext().getReportName() + "@" + LOAD_FLOW_TYPE_REPORT;
                        rootReporter = new ReporterModel(rootReporterId, rootReporterId);
                        reporter = rootReporter.createSubReporter(LOAD_FLOW_TYPE_REPORT, LOAD_FLOW_TYPE_REPORT + " (${providerToUse})", "providerToUse", context.getProvider());
                    }

                    Reporter finalRootReporter1 = rootReporter;
                    return Mono.fromFuture(runASAsync(tuple, context.getVariantId() != null ? context.getVariantId() : VariantManagerConstants.INITIAL_VARIANT_ID, params, runner, reporter))
                            .flatMap(result -> {
                                if (context.getReportContext().getReportId() != null) {
                                    Reporter finalRootReporter = finalRootReporter1;
                                    return reportService.sendReport(context.getReportContext().getReportId(), finalRootReporter)
                                            .thenReturn(result);
                                } else {
                                    return Mono.just(result);
                                }
                            });
                });
    }
}
