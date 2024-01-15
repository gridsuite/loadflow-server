package org.gridsuite.loadflow.server.service.computation;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.gridsuite.loadflow.server.repositories.computation.AbstractComputationResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @param <R> run context specific to a computation, including parameters
 * @param <Repo> result repository specific to the computation
 */
public abstract class AbstractComputationService<R, Repo extends AbstractComputationResultRepository> {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractComputationService.class);

    protected ObjectMapper objectMapper;
    protected NotificationService notificationService;
    @Getter
    protected String defaultProvider;
    protected UuidGeneratorService uuidGeneratorService;
    protected Repo resultRepository;

    protected AbstractComputationService(NotificationService notificationService, Repo resultRepository,
                                         ObjectMapper objectMapper, UuidGeneratorService uuidGeneratorService,
                                         String defaultProvider) {
        this.notificationService = Objects.requireNonNull(notificationService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.uuidGeneratorService = Objects.requireNonNull(uuidGeneratorService);
        this.defaultProvider = Objects.requireNonNull(defaultProvider);
        this.resultRepository = Objects.requireNonNull(resultRepository);
    }

    public void stop(UUID resultUuid, String receiver) {
        notificationService.sendCancelMessage(new CancelContext(resultUuid, receiver).toMessage());
    }

    public abstract List<String> getProviders();

    public abstract UUID runAndSaveResult(R runContext);

    public void deleteResult(UUID resultUuid) {
        resultRepository.delete(resultUuid);
    }

    public void deleteResults(List<UUID> resultUuids) {
        if (resultUuids != null && !resultUuids.isEmpty()) {
            resultUuids.forEach(resultRepository::delete);
        } else {
            deleteResults();
        }
    }

    public void deleteResults() {
        resultRepository.deleteAll();
    }
}
