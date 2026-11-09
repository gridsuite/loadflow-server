/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.RatioTapChanger;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.regulation.RegulationMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */

class NetworksTest {

    private static void assertLocalVoltageRegulatingGenerator(Generator generator) {
        assertEquals(24.5, generator.getLocalTargetV(), 0.0);
        assertEquals(301.0, generator.getLocalTargetQ(), 0.0);
        assertTrue(generator.isRegulatingWithMode(RegulationMode.VOLTAGE));
        assertEquals(24.5, generator.getRegulatingTargetV(), 0.0);
    }

    @Test
    void testCreateNetworkWithoutFailingVariant() {
        Network network = Networks.createNetwork(false);
        assertNotNull(network);
        assertNull(network.getGenerator("FAILING_GEN"));
        assertTrue(network.getVariantManager().getVariantIds().contains(Networks.VARIANT_3_ID));
    }

    @Test
    void testCreateNetworkWithFailingVariant() {
        Network network = Networks.createNetwork(true);
        assertEquals(VariantManagerConstants.INITIAL_VARIANT_ID, network.getVariantManager().getWorkingVariantId());

        network.getVariantManager().setWorkingVariant(Networks.VARIANT_3_ID);
        Generator failingGen = network.getGenerator("FAILING_GEN");
        assertNotNull(failingGen);
        assertEquals(20000., failingGen.getTargetP(), 0.0);
        assertLocalVoltageRegulatingGenerator(failingGen);
    }

    @Test
    void testCreateNetworkWithPrefix() {
        Network network = Networks.createNetwork("pref_", true);
        assertTrue(network.getVariantManager().getVariantIds().contains(Networks.VARIANT_1_ID));

        Generator generator = network.getGenerator("pref_GEN");
        assertNotNull(generator);
        assertEquals(607.0, generator.getTargetP(), 0.0);
        assertLocalVoltageRegulatingGenerator(generator);

        TwoWindingsTransformer twt = network.getTwoWindingsTransformer("pref_NHV2_NLOAD");
        RatioTapChanger rtc = twt.getRatioTapChanger();
        assertNotNull(rtc);
        assertTrue(rtc.isRegulatingWithMode(RegulationMode.VOLTAGE));
        assertEquals(158.0, rtc.getRegulatingTargetV(), 0.0);
        assertEquals(0.0, rtc.getVoltageRegulation().getTargetDeadband(), 0.0);
        assertSame(twt.getTerminal2(), rtc.getRegulatingTerminal());
    }
}
