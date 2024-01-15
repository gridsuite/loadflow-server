package org.gridsuite.loadflow.server.service.computation;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.gridsuite.loadflow.server.utils.ReportContext;

import java.util.UUID;

/**
 * @param <P> parameters structure specific to the computation
 */
@Getter
@AllArgsConstructor
public class ComputationRunContext<P> {
    private final UUID networkUuid;
    private final String variantId;
    private final String receiver;
    private final String provider;
    private final ReportContext reportContext;
    private final String userId;
    private final Float limitReduction;
    protected final P parameters;
}
