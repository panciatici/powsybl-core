/**
 * Copyright (c) 2016, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.xml;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.test.ThreeWindingsTransformerNetworkFactory;
import org.junit.Test;

import java.io.IOException;

import static com.powsybl.iidm.xml.IidmXmlConstants.CURRENT_IIDM_XML_VERSION;

/**
 * @author Luma Zamarreno <zamarrenolm at aia.es>
 */
public class ThreeWindingsTransformerXmlTest extends AbstractXmlConverterTest {

    @Test
    public void roundTripTest() throws IOException {
        roundTripVersionnedXmlTest("threeWindingsTransformerRoundTripRef.xml", IidmXmlVersion.V_1_0);

        roundTripXmlTest(ThreeWindingsTransformerNetworkFactory.createWithCurrentLimits(),
                NetworkXml::writeAndValidate,
                NetworkXml::read,
                getVersionDir(CURRENT_IIDM_XML_VERSION) + "threeWindingsTransformerRoundTripRef.xml");
    }

    @Test
    public void allTapChangersRoundTripTest() throws IOException {
        roundTripXmlTest(createNetworkWithAllTapChangers(),
                NetworkXml::writeAndValidate,
                NetworkXml::read,
                getVersionDir(CURRENT_IIDM_XML_VERSION) + "threeWindingsTransformerWithAllTcRoundTripRef.xml");
    }

    private static Network createNetworkWithAllTapChangers() {
        Network network = ThreeWindingsTransformerNetworkFactory.createWithCurrentLimits();

        ThreeWindingsTransformer twt = network.getThreeWindingsTransformer("3WT");
        Terminal regulatingTerminal = network.getLoad("LOAD_11").getTerminal();
        createRtc(twt.getLeg1(), regulatingTerminal);
        createPtc(twt.getLeg1(), regulatingTerminal);
        createPtc(twt.getLeg2(), regulatingTerminal);
        createPtc(twt.getLeg3(), regulatingTerminal);

        return network;
    }

    private static void createRtc(ThreeWindingsTransformer.Leg leg, Terminal regulatingTerminal) {
        leg.newRatioTapChanger()
                .beginStep()
                .setRho(0.9)
                .setR(0.9801)
                .setX(0.09801)
                .setG(0.08264462809917356)
                .setB(0.008264462809917356)
                .endStep()
                .beginStep()
                .setRho(1.0)
                .setR(1.089)
                .setX(0.1089)
                .setG(0.09182736455463728)
                .setB(0.009182736455463728)
                .endStep()
                .beginStep()
                .setRho(1.1)
                .setR(1.1979)
                .setX(0.11979)
                .setG(0.10101010101010101)
                .setB(0.010101010101010101)
                .endStep()
                .setTapPosition(2)
                .setLoadTapChangingCapabilities(true)
                .setRegulating(false)
                .setTargetV(33.0)
                .setRegulationTerminal(regulatingTerminal)
                .add();
    }

    private static void createPtc(ThreeWindingsTransformer.Leg leg, Terminal regulatingTerminal) {
        leg.newPhaseTapChanger()
                .setTapPosition(1)
                .setRegulationTerminal(regulatingTerminal)
                .setRegulationMode(PhaseTapChanger.RegulationMode.FIXED_TAP)
                .setRegulationValue(200)
                .beginStep()
                .setAlpha(-20.0)
                .setRho(1.0)
                .setR(0.0)
                .setX(0.0)
                .setG(0.0)
                .setB(0.0)
                .endStep()
                .beginStep()
                .setAlpha(0.0)
                .setRho(1.0)
                .setR(0.0)
                .setX(0.0)
                .setG(0.0)
                .setB(0.0)
                .endStep()
                .beginStep()
                .setAlpha(20.0)
                .setRho(1.0)
                .setR(0.0)
                .setX(0.0)
                .setG(0.0)
                .setB(0.0)
                .endStep()
                .add();
    }
}
