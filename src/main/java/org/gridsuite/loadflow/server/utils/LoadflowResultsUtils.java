package org.gridsuite.loadflow.server.utils;

import com.powsybl.iidm.network.*;
import com.powsybl.security.BusBreakerViolationLocation;
import com.powsybl.security.LimitViolation;
import com.powsybl.security.NodeBreakerViolationLocation;
import com.powsybl.security.ViolationLocation;

import java.util.*;

import static com.powsybl.security.ViolationLocation.Type.NODE_BREAKER;

public final class LoadflowResultsUtils {

    private LoadflowResultsUtils() {
    }

    public static String getViolationLocationId(LimitViolation limitViolation, Network network) {
        Optional<ViolationLocation> violationLocation = limitViolation.getViolationLocation();
        if (violationLocation.isEmpty()) {
            return limitViolation.getSubjectId();
        }

        ViolationLocation location = violationLocation.get();
        if (location.getType() == NODE_BREAKER) {
            NodeBreakerViolationLocation nodeBreakerViolationLocation = (NodeBreakerViolationLocation) location;
            return getBusIdOrVlIdNodeBreaker(nodeBreakerViolationLocation, network);
        } else {
            BusBreakerViolationLocation busBreakerViolationLocation = (BusBreakerViolationLocation) location;
            return getBusIdOrVlIdBusBreaker(busBreakerViolationLocation, network, limitViolation.getSubjectId());
        }
    }

    public static String getBusIdOrVlIdNodeBreaker(NodeBreakerViolationLocation nodeBreakerViolationLocation, Network network) {
        VoltageLevel vl = network.getVoltageLevel(nodeBreakerViolationLocation.getVoltageLevelId());

        List<String> busBarIds = nodeBreakerViolationLocation.getNodes().stream()
                .map(node -> vl.getNodeBreakerView().getTerminal(node))
                .filter(Objects::nonNull)
                .map(Terminal::getConnectable)
                .filter(t -> t.getType() == IdentifiableType.BUSBAR_SECTION)
                .map(Identifiable::getId)
                .distinct()
                .toList();

        return formatNodeId(busBarIds, nodeBreakerViolationLocation.getVoltageLevelId());
    }

    public static String formatNodeId(List<String> nodesIds, String subjectId) {
        if (nodesIds.size() == 1) {
            return nodesIds.get(0);
        } else if (nodesIds.isEmpty()) {
            return subjectId;
        } else {
            return subjectId + " (" + String.join(", ", nodesIds) + " )";
        }
    }

    public static String getBusIdOrVlIdBusBreaker(BusBreakerViolationLocation busBreakerViolationLocation, Network network, String subjectId) {
        List<String> busBreakerIds = busBreakerViolationLocation
                .getBusBreakerView(network)
                .getBusStream()
                .map(Identifiable::getId)
                .distinct().toList();
        return formatNodeId(busBreakerIds, subjectId);
    }

}
