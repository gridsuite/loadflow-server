/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.repositories.parameters;

import org.gridsuite.loadflow.server.entities.SlackBusResultEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

/**
 * @author AJELLAL Ali <ali.ajellal@rte-france.com>
 */
public interface SlackBusResultRepository extends JpaRepository<SlackBusResultEntity, UUID>, JpaSpecificationExecutor<SlackBusResultEntity> {
    List<SlackBusResultEntity> findAll(Specification specification);

}
