/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.computation.utils;

import com.powsybl.commons.PowsyblException;
import org.springframework.messaging.MessageHeaders;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
public final class ContextUtils {
    public static final int MSG_MAX_LENGTH = 256;

    private ContextUtils() { }

    public static String getNonNullHeader(MessageHeaders headers, String name) {
        String header = (String) headers.get(name);
        if (header == null) {
            throw new PowsyblException("Header '" + name + "' not found");
        }
        return header;
    }

    // prevent the message from being too long for rabbitmq
    // the beginning and ending are both kept, it should make it easier to identify
    public static String shortenMessage(String msg) {
        if (msg == null) {
            return null;
        }

        return msg.length() > MSG_MAX_LENGTH ?
                msg.substring(0, MSG_MAX_LENGTH / 2) + " ... " + msg.substring(msg.length() - MSG_MAX_LENGTH / 2, msg.length() - 1)
                : msg;
    }
}
