/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.dto;

import com.powsybl.commons.parameters.ParameterType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import java.util.List;

/**
 * @author David Braquart <david.braquart@rte-france.com>
 */
@Getter
@AllArgsConstructor
@Builder
public class ParameterInfos {

    private final String name;

    private final ParameterType type;

    private final String description;

    private final Object defaultValue;

    private final List<Object> possibleValues;
}



