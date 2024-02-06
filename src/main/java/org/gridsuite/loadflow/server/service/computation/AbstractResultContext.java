/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service.computation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.loadflow.server.service.computation.NotificationService.*;

/**
 * @author Mathieu Deharbe <mathieu.deharbe at rte-france.com
 * @param <R> run context specific to a computation, including parameters
 */
@Getter
public abstract class AbstractResultContext<R extends AbstractComputationRunContext> {

    protected static final String REPORT_UUID_HEADER = "reportUuid";

    public static final String VARIANT_ID_HEADER = "variantId";

    public static final String REPORTER_ID_HEADER = "reporterId";

    public static final String REPORT_TYPE_HEADER = "reportType";

    protected static final String MESSAGE_ROOT_NAME = "parameters";

    protected final UUID resultUuid;

    protected final R runContext;

    protected AbstractResultContext(UUID resultUuid, R runContext) {
        this.resultUuid = Objects.requireNonNull(resultUuid);
        this.runContext = Objects.requireNonNull(runContext);
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