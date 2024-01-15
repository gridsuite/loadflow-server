package org.gridsuite.loadflow.server.repositories.computation;

import java.util.UUID;

public abstract class AbstractComputationResultRepository {
    public abstract void delete(UUID resultUuid);

    public abstract void deleteAll();
}
