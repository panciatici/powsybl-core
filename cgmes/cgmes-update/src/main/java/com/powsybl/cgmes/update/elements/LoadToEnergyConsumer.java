package com.powsybl.cgmes.update.elements;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import com.powsybl.cgmes.model.CgmesModel;
import com.powsybl.cgmes.update.CgmesPredicateDetails;
import com.powsybl.cgmes.update.ConversionMapper;
import com.powsybl.cgmes.update.IidmChange;
import com.powsybl.cgmes.update.IidmToCgmes;
import com.powsybl.iidm.network.Load;
import com.powsybl.triplestore.api.PropertyBag;
import com.powsybl.triplestore.api.PropertyBags;

public class LoadToEnergyConsumer extends IidmToCgmes implements ConversionMapper {

    public LoadToEnergyConsumer(IidmChange change, CgmesModel cgmes) {
        super(change, cgmes);
    }

    @Override
    public Map<String, Object> mapIidmToCgmesPredicates() {
        return Collections.unmodifiableMap(Stream.of(
            entry("name", new CgmesPredicateDetails("cim:IdentifiedObject.name", "_EQ", false)),
            entry("p0",
                new CgmesPredicateDetails("cim:LoadResponseCharacteristic.pConstantPower", "_EQ", false,
                    loadResponseCharacteristicId)),
            entry("q0",
                new CgmesPredicateDetails("cim:LoadResponseCharacteristic.qConstantPower", "_EQ", false,
                    loadResponseCharacteristicId)))
            .collect(entriesToMap()));
    }

    @Override
    public Map<CgmesPredicateDetails, String> getAllCgmesDetailsOnCreate() {

        Map<CgmesPredicateDetails, String> allCgmesDetails = new HashMap<CgmesPredicateDetails, String>();
        Load newLoad = (Load) change.getIdentifiable();

        CgmesPredicateDetails rdfType = new CgmesPredicateDetails("rdf:type", "_EQ", false);
        allCgmesDetails.put(rdfType, "cim:EnergyConsumer");

        String name = newLoad.getName();
        if (name != null) {
            allCgmesDetails.put((CgmesPredicateDetails) mapIidmToCgmesPredicates().get("name"),
                name);
        }

        String voltageLevelId = newLoad.getTerminal().getVoltageLevel().getId();
        CgmesPredicateDetails equipmentContainer = new CgmesPredicateDetails(
            "cim:Equipment.MemberOf_EquipmentContainer", "_EQ", true);
        if (!voltageLevelId.equals("NaN")) {
            allCgmesDetails.put(equipmentContainer, voltageLevelId);
        }

        CgmesPredicateDetails energyConsumerLoadResponse = new CgmesPredicateDetails("cim:EnergyConsumer.LoadResponse",
            "_EQ", true);
        allCgmesDetails.put(energyConsumerLoadResponse, loadResponseCharacteristicId);

        /**
         * Create LoadResponseCharacteristic element
         */

        CgmesPredicateDetails rdfTypeLRC = new CgmesPredicateDetails("rdf:type", "_EQ", false,
            loadResponseCharacteristicId);
        allCgmesDetails.put(rdfTypeLRC, "cim:LoadResponseCharacteristic");

        CgmesPredicateDetails nameLRC = new CgmesPredicateDetails("cim:IdentifiedObject.name", "_EQ", false,
            loadResponseCharacteristicId);
        allCgmesDetails.put(nameLRC, name.concat("_LRC"));

        double p0 = newLoad.getP0();
        if (!String.valueOf(p0).equals("NaN")) {
            allCgmesDetails.put((CgmesPredicateDetails) mapIidmToCgmesPredicates().get("p0"),
                String.valueOf(p0));
        }

        double q0 = newLoad.getQ0();
        if (!String.valueOf(q0).equals("NaN")) {
            allCgmesDetails.put((CgmesPredicateDetails) mapIidmToCgmesPredicates().get("q0"),
                String.valueOf(q0));
        }

        return allCgmesDetails;
    }

    /**
     * Check if EnergyConsumer.LoadResponse element already exists in grid, if yes -
     * returns the id
     *
     */
    private String getLoadResponseCharacteristicId() {

        PropertyBags energyConsumers = cgmes.energyConsumers();
        Iterator i = energyConsumers.iterator();
        while (i.hasNext()) {
            PropertyBag pb = (PropertyBag) i.next();
            if (pb.getId("EnergyConsumer").equals(change.getIdentifiableId())) {
                return pb.getId("LoadResponse");
            } else {
                continue;
            }
        }
        return UUID.randomUUID().toString();
    }

    private String loadResponseCharacteristicId = getLoadResponseCharacteristicId();

}
