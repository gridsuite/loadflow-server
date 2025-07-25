/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.computation.dto.ReportInfos;
import org.gridsuite.computation.service.AbstractResultContext;
import org.gridsuite.computation.utils.MessageUtils;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersValues;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.computation.service.NotificationService.*;

public class LoadFlowResultContext extends AbstractResultContext<LoadFlowRunContext> {

    private static final String HEADER_SECURITY_MOE = "securityMode";

    public LoadFlowResultContext(UUID resultUuid, LoadFlowRunContext runContext) {
        super(resultUuid, runContext);
    }

    @Override
    protected Map<String, String> getSpecificMsgHeaders(ObjectMapper ignoredObjectMapper) {
        return Map.of(HEADER_SECURITY_MOE, Boolean.toString(getRunContext().isSecurityMode()));
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
        Boolean isModeSecurity = Boolean.parseBoolean((String)headers.get(HEADER_SECURITY_MOE));

        LoadFlowParametersValues parameters;
        try {
            parameters = objectMapper.readValue(message.getPayload(), LoadFlowParametersValues.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        UUID reportUuid = headers.get(REPORT_UUID_HEADER) != null ? UUID.fromString((String) headers.get(REPORT_UUID_HEADER)) : null;
        String reporterId = headers.containsKey(REPORTER_ID_HEADER) ? (String) headers.get(REPORTER_ID_HEADER) : null;
        String reportType = headers.containsKey(REPORT_TYPE_HEADER) ? (String) headers.get(REPORT_TYPE_HEADER) : null;

        LoadFlowRunContext runContext =
                LoadFlowRunContext.builder()
                        .networkUuid(networkUuid)
                        .variantId(variantId)
                        .receiver(receiver)
                        .provider(provider)
                        .parameters(parameters)
                        .withRatioTapChangers(parameters.getCommonParameters().isTransformerVoltageControlOn())
                        .isModeSecurity(isModeSecurity)
                        .reportInfos(ReportInfos.builder().reportUuid(reportUuid).reporterId(reporterId).computationType(reportType).build())
                        .userId(userId)
                        .build();

        return new LoadFlowResultContext(resultUuid, runContext);
    }
}
