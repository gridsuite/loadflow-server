/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersValues;
import org.gridsuite.loadflow.server.utils.ReportContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import java.io.UncheckedIOException;
import java.util.*;

import static org.gridsuite.loadflow.server.service.NotificationService.*;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@Getter
public class LoadFlowResultContext {

    private static final String REPORT_UUID_HEADER = "reportUuid";

    public static final String VARIANT_ID_HEADER = "variantId";

    public static final String REPORTER_ID_HEADER = "reporterId";

    public static final String REPORT_TYPE_HEADER = "reportType";

    private static final String MESSAGE_ROOT_NAME = "parameters";

    private final UUID resultUuid;

    private final LoadFlowRunContext runContext;

    public LoadFlowResultContext(UUID resultUuid, LoadFlowRunContext runContext) {
        this.resultUuid = Objects.requireNonNull(resultUuid);
        this.runContext = Objects.requireNonNull(runContext);
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

        LoadFlowParametersValues parameters;
        try {
            // can't use the following line because jackson doesn't unwrap null in the rootname
            // -> '{"parameters": null}' throws instead returning null
            //     MismatchedInputException: Cannot deserialize value of type `LoadFlowParametersInfos` from Null value (token `JsonToken.VALUE_NULL`)
            // parameters = objectMapper.reader().withRootName(MESSAGE_ROOT_NAME).readValue(message.getPayload(), LoadFlowParametersInfos.class);
            parameters = objectMapper.treeToValue(objectMapper.readTree(message.getPayload()).get(MESSAGE_ROOT_NAME), LoadFlowParametersValues.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        UUID reportUuid = headers.containsKey(REPORT_UUID_HEADER) ? UUID.fromString((String) headers.get(REPORT_UUID_HEADER)) : null;
        String reporterId = headers.containsKey(REPORTER_ID_HEADER) ? (String) headers.get(REPORTER_ID_HEADER) : null;
        String reportType = headers.containsKey(REPORT_TYPE_HEADER) ? (String) headers.get(REPORT_TYPE_HEADER) : null;
        Float limitReduction = headers.containsKey(HEADER_LIMIT_REDUCTION) ? Float.parseFloat((String) headers.get(HEADER_LIMIT_REDUCTION)) : null;

        LoadFlowRunContext runContext =
                LoadFlowRunContext.builder()
                        .networkUuid(networkUuid)
                        .variantId(variantId)
                        .receiver(receiver)
                        .provider(provider)
                        .parameters(parameters)
                        .reportContext(ReportContext.builder().reportId(reportUuid).reportName(reporterId).reportType(reportType).build())
                        .userId(userId)
                        .limitReduction(limitReduction)
                        .build();

        return new LoadFlowResultContext(resultUuid, runContext);
    }

    public Message<String> toMessage(ObjectMapper objectMapper) {
        String parametersJson;
        try {
            // can't use the following line because jackson doesn't wrap null in the rootname
            // -> outputs 'null' instead of '{"parameters": null}'
            // parametersJson = objectMapper.writer().withRootName(MESSAGE_ROOT_NAME).writeValueAsString(runContext.getParameters());
            parametersJson = objectMapper.writeValueAsString(objectMapper.createObjectNode().putPOJO(MESSAGE_ROOT_NAME, runContext.getParameters()));
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        return MessageBuilder.withPayload(parametersJson)
                .setHeader("resultUuid", resultUuid.toString())
                .setHeader("networkUuid", runContext.getNetworkUuid().toString())
                .setHeader(VARIANT_ID_HEADER, runContext.getVariantId())
                .setHeader(HEADER_RECEIVER, runContext.getReceiver())
                .setHeader(HEADER_PROVIDER, runContext.getProvider())
                .setHeader(HEADER_USER_ID, runContext.getUserId())
                .setHeader(REPORT_UUID_HEADER, runContext.getReportContext().getReportId() != null ? runContext.getReportContext().getReportId().toString() : null)
                .setHeader(REPORTER_ID_HEADER, runContext.getReportContext().getReportName())
                .setHeader(REPORT_TYPE_HEADER, runContext.getReportContext().getReportType())
                .setHeader(HEADER_LIMIT_REDUCTION, runContext.getLimitReduction() != null ? runContext.getLimitReduction().toString() : null)
            .build();
    }
}
