/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.entities;

import lombok.*;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "loadflow_result")
public class LoadFlowResultEntity {

    @Id
    private UUID resultUuid;

    @Column(columnDefinition = "timestamptz")
    private Instant writeTimeStamp;

    @Column(columnDefinition = "CLOB")
    String modifications;

    @Setter
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "loadFlowResult", fetch = FetchType.LAZY)
    private List<ComponentResultEntity> componentResults;

    @Setter
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "loadFlowResult", fetch = FetchType.LAZY)
    private List<LimitViolationEntity> limitViolations;

    @Setter
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "loadFlowResult", fetch = FetchType.LAZY)
    private List<CountryAdequacyEntity> countryAdequacies;

    @Setter
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "loadFlowResult", fetch = FetchType.LAZY)
    private List<ExchangeMapEntryEntity> exchanges;
}
