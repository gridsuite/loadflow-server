/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.computation.service;

import lombok.Getter;
import org.gridsuite.loadflow.server.service.LoadFlowService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Objects;
import java.util.UUID;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@Getter
public class CancelContext {

    private final UUID resultUuid;

    private final String receiver;

    public CancelContext(UUID resultUuid, String receiver) {
        this.resultUuid = Objects.requireNonNull(resultUuid);
        this.receiver = Objects.requireNonNull(receiver);
    }

    public static CancelContext fromMessage(Message<String> message) {
        Objects.requireNonNull(message);
        MessageHeaders headers = message.getHeaders();
        UUID resultUuid = UUID.fromString(LoadFlowService.getNonNullHeader(headers, "resultUuid"));
        String receiver = (String) headers.get("receiver");
        return new CancelContext(resultUuid, receiver);
    }

    public Message<String> toMessage() {
        return MessageBuilder.withPayload("")
                .setHeader("resultUuid", resultUuid.toString())
                .setHeader("receiver", receiver)
                .build();
    }
}
