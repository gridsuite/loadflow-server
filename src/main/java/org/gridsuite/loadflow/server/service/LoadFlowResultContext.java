/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import lombok.Getter;
import org.gridsuite.loadflow.server.dto.LoadFlowParametersInfos;
import org.gridsuite.loadflow.server.utils.ReportContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.gridsuite.loadflow.server.service.LoadFlowRunContext.buildParameters;
import static org.gridsuite.loadflow.server.service.NotificationService.*;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@Getter
public class LoadFlowResultContext {

    private static final String REPORT_UUID_HEADER = "reportUuid";

    public static final String VARIANT_ID_HEADER = "variantId";

    public static final String REPORTER_ID_HEADER = "reporterId";

    private final UUID resultUuid;

    private final LoadFlowRunContext runContext;

    public LoadFlowResultContext(UUID resultUuid, LoadFlowRunContext runContext) {
        this.resultUuid = Objects.requireNonNull(resultUuid);
        this.runContext = Objects.requireNonNull(runContext);
    }

    private static List<UUID> getHeaderList(MessageHeaders headers, String name) {
        String header = (String) headers.get(name);
        if (header == null || header.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(header.split(",")).stream()
                .map(UUID::fromString)
                .collect(Collectors.toList());
    }

    public static LoadFlowResultContext fromMessage(Message<String> message, ObjectMapper objectMapper) {
        Objects.requireNonNull(message);
        MessageHeaders headers = message.getHeaders();
        UUID resultUuid = UUID.fromString(LoadFlowService.getNonNullHeader(headers, "resultUuid"));
        UUID networkUuid = UUID.fromString(LoadFlowService.getNonNullHeader(headers, "networkUuid"));
        String variantId = (String) headers.get(VARIANT_ID_HEADER);
        String receiver = (String) headers.get(HEADER_RECEIVER);
        String provider = (String) headers.get(HEADER_PROVIDER);
        String userId = (String) headers.get(HEADER_USER_ID);
        List<UUID> otherNetworkUuids = getHeaderList(headers, "otherNetworkUuids");

        LoadFlowParametersInfos parameters;
        try {
            parameters = objectMapper.readValue(message.getPayload(), LoadFlowParametersInfos.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        UUID reportUuid = headers.containsKey(REPORT_UUID_HEADER) ? UUID.fromString((String) headers.get(REPORT_UUID_HEADER)) : null;
        String reporterId = headers.containsKey(REPORTER_ID_HEADER) ? (String) headers.get(REPORTER_ID_HEADER) : null;
        LoadFlowRunContext runContext =
                LoadFlowRunContext.builder()
                        .networkUuid(networkUuid)
                        .variantId(variantId)
                        .otherNetworksUuids(otherNetworkUuids)
                        .receiver(receiver)
                        .provider(provider)
                        .parameters(buildParameters(parameters, provider))
                        .reportContext(ReportContext.builder().reportId(reportUuid).reportName(reporterId).build())
                        .userId(userId)
                        .build();

        return new LoadFlowResultContext(resultUuid, runContext);
    }

    public Message<String> toMessage(ObjectMapper objectMapper) {
        String parametersJson;
        try {
            parametersJson = objectMapper.writeValueAsString(runContext.getParameters());
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        return MessageBuilder.withPayload(parametersJson)
                .setHeader("resultUuid", resultUuid.toString())
                .setHeader("networkUuid", runContext.getNetworkUuid().toString())
                .setHeader(VARIANT_ID_HEADER, runContext.getVariantId())
                .setHeader("otherNetworkUuids", runContext.getOtherNetworksUuids().stream().map(UUID::toString).collect(Collectors.joining(",")))
                .setHeader(HEADER_RECEIVER, runContext.getReceiver())
                .setHeader(HEADER_PROVIDER, runContext.getProvider())
                .setHeader(HEADER_USER_ID, runContext.getUserId())
                .setHeader(REPORT_UUID_HEADER, runContext.getReportContext().getReportId() != null ? runContext.getReportContext().getReportId().toString() : null)
                .setHeader(REPORTER_ID_HEADER, runContext.getReportContext().getReportName())
                .build();
    }
}
