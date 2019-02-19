/**
 * Copyright (c) 2016, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.xml;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.ThreeWindingsTransformerNetworkFactory;
import com.powsybl.iidm.network.util.ImmutableNetwork;
import org.junit.Test;

import java.io.IOException;

/**
 * @author Luma Zamarreno <zamarrenolm at aia.es>
 */
public class ThreeWindingsTransformerXmlTest extends AbstractNetworkXmlTest {

    private static final String REF = "/threeWindingsTransformerRoundTripRef.xml";
    private static final Network NETWORK = ThreeWindingsTransformerNetworkFactory.createWithCurrentLimits();

    @Test
    public void testReadImmutable() {
        writeToXmlTest(new ImmutableNetwork(NETWORK), REF);
    }

    @Test
    public void roundTripTest() throws IOException {
        roundTripXmlTest(NETWORK,
                         NetworkXml::writeAndValidate,
                         NetworkXml::read,
                         REF);
    }
}
