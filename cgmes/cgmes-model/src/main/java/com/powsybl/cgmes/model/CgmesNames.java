/**
 * Copyright (c) 2017-2018, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.powsybl.cgmes.model;

/**
 * @author Luma Zamarreño <zamarrenolm at aia.es>
 */
public final class CgmesNames {

    public static final String FULL_MODEL = "FullModel";
    public static final String SCENARIO_TIME = "scenarioTime";
    public static final String CREATED = "created";
    public static final String DESCRIPTION = "description";
    public static final String VERSION = "version";
    public static final String DEPENDENT_ON = "DependentOn";
    public static final String PROFILE = "profile";
    public static final String MODELING_AUTHORITY_SET = "modelingAuthoritySet";

    public static final String SUBSTATION = "Substation";
    public static final String VOLTAGE_LEVEL = "VoltageLevel";
    public static final String TERMINAL = "Terminal";
    public static final String AC_LINE_SEGMENT = "ACLineSegment";
    public static final String SERIES_COMPENSATOR = "SeriesCompensator";
    public static final String TOPOLOGICAL_NODE = "TopologicalNode";
    public static final String CONDUCTING_EQUIPMENT = "ConductingEquipment";

    public static final String TRANSFORMER_WINDING_RATED_U = "transformerWindingRatedU";
    public static final String TRANSFORMER_END = "TransformerEnd";
    public static final String TAP_CHANGER = "TapChanger";
    public static final String CONTINUOUS_POSITION = "continuousPosition";
    public static final String POSITION = "position";
    public static final String LOW_STEP = "lowStep";
    public static final String HIGH_STEP = "highStep";

    public static final String DC_TERMINAL = "DCTerminal";
    public static final String RATED_UDC = "ratedUdc";

    public static final String B_PER_SECTION = "bPerSection";

    public static final String VOLTAGE = "v";
    public static final String ANGLE = "angle";

    public static final String RATIO_TAP_CHANGER_TABLE = "RatioTapChangerTable";
    public static final String PHASE_TAP_CHANGER_TABLE = "PhaseTapChangerTable";

    public static final String TERMINAL1 = "Terminal1";
    public static final String TERMINAL2 = "Terminal2";

    public static final String TOPOLOGICAL_ISLAND = "TopologicalIsland";
    public static final String ANGLEREF_TOPOLOGICALNODE = "AngleRefTopologicalNode";
    public static final String TOPOLOGICAL_NODES = "TopologicalNodes";

    private CgmesNames() {
    }
}
