/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.xml.util;

import com.powsybl.commons.xml.XmlUtil;
import com.powsybl.iidm.xml.IidmXmlVersion;
import com.powsybl.iidm.xml.NetworkXmlReaderContext;

/**
 * @author Miora Ralambotiana <miora.ralambotiana at rte-france.com>
 */
public final class IidmXmlUtil {

    public static double readDoubleAttributeFromVersion(IidmXmlVersion version, String attributeName, NetworkXmlReaderContext context) {
        return readDoubleAttributeFromVersion(version, attributeName, Double.NaN, context);
    }

    /**
     * If the file's version equals or is more recent than a given version, a double attribute is read at the given attribute name and returned.
     * Else, the default value for the attribute is returned.
     */
    public static double readDoubleAttributeFromVersion(IidmXmlVersion version, String attributeName, double defaultValue, NetworkXmlReaderContext context) {
        if (IidmXmlVersion.compare(context.getVersion(), version) >= 0) {
            return XmlUtil.readDoubleAttribute(context.getReader(), attributeName);
        }
        return defaultValue;
    }

    public static double readDoubleAttributeUntilVersion(IidmXmlVersion version, String attributeName, NetworkXmlReaderContext context) {
        return readDoubleAttributeUntilVersion(version, attributeName, Double.NaN, context);
    }

    /**
     * If the file's version equals or is older than a given version, a double attribute is read at the given attribute name and returned.
     * Else, the default value for the attribute is returned.
     */
    public static double readDoubleAttributeUntilVersion(IidmXmlVersion version, String attributeName, double defaultValue, NetworkXmlReaderContext context) {
        if (IidmXmlVersion.compare(context.getVersion(), version) < 0) {
            return XmlUtil.readDoubleAttribute(context.getReader(), attributeName);
        }
        return defaultValue;
    }

    private IidmXmlUtil() {
    }
}
