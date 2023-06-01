/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.powsybl.commons.reporter.Reporter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@Service
public class ReportService {

    static final String REPORT_API_VERSION = "v1";

    private final WebClient webClient;

    @Autowired
    public ReportService(WebClient.Builder builder,
                         @Value("${gridsuite.services.report-server.base-uri:http://report-server/}") String baseUri) {
        webClient = builder.baseUrl(baseUri)
                .build();
    }

    public ReportService(WebClient webClient) {
        this.webClient = Objects.requireNonNull(webClient);
    }

    public Mono<Void> sendReport(UUID reportUuid, Reporter reporter) {
        Objects.requireNonNull(reportUuid);
        return webClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path(REPORT_API_VERSION + "/reports/{reportUuid}")
                        .build(reportUuid))
                .body(BodyInserters.fromValue(reporter))
                .retrieve()
                .bodyToMono(Void.class);
    }
}
