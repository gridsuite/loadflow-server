package org.gridsuite.loadflow.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author maissa Souissi <maissa.souissi at rte-france.com>
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GlobalFilter {
    String nominalV;
    String countryCode;
}
