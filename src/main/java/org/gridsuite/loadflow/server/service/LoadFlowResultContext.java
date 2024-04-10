/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.loadflow.server.computation.utils.MessageUtils;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersValues;
import org.gridsuite.loadflow.server.computation.service.AbstractResultContext;
import org.gridsuite.loadflow.server.computation.dto.ReportInfos;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.loadflow.server.computation.service.NotificationService.*;

public class LoadFlowResultContext extends AbstractResultContext<LoadFlowRunContext> {
    public static final String HEADER_LIMIT_REDUCTION = "limitReduction";

    public LoadFlowResultContext(UUID resultUuid, LoadFlowRunContext runContext) {
        super(resultUuid, runContext);
    }

    public static LoadFlowResultContext fromMessage(Message<String> message, ObjectMapper objectMapper) {
        Objects.requireNonNull(message);
        MessageHeaders headers = message.getHeaders();
        UUID resultUuid = UUID.fromString(MessageUtils.getNonNullHeader(headers, RESULT_UUID_HEADER));
        UUID networkUuid = UUID.fromString(MessageUtils.getNonNullHeader(headers, NETWORK_UUID_HEADER));
        String variantId = (String) headers.get(VARIANT_ID_HEADER);
        String receiver = (String) headers.get(HEADER_RECEIVER);
        String provider = (String) headers.get(HEADER_PROVIDER);
        String userId = (String) headers.get(HEADER_USER_ID);

        LoadFlowParametersValues parameters;
        try {
            parameters = objectMapper.readValue(message.getPayload(), LoadFlowParametersValues.class);
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
                        .reportInfos(ReportInfos.builder().reportUuid(reportUuid).reporterId(reporterId).reportType(reportType).build())
                        .userId(userId)
                        .limitReduction(limitReduction)
                        .build();

        return new LoadFlowResultContext(resultUuid, runContext);
    }

    @Override
    protected Map<String, String> getSpecificMsgHeaders() {
        return Map.of(
                HEADER_LIMIT_REDUCTION,
                runContext.getLimitReduction() != null ?
                        runContext.getLimitReduction().toString() :
                        null);
    }
}
