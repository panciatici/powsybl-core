/**
 * Copyright (c) 2016, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.xml;

import com.powsybl.commons.xml.XmlReaderContext;
import com.powsybl.iidm.AbstractConverterContext;
import com.powsybl.iidm.anonymizer.Anonymizer;
import com.powsybl.iidm.import_.ImportOptions;

import javax.xml.stream.XMLStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class NetworkXmlReaderContext extends AbstractConverterContext<ImportOptions> implements XmlReaderContext {

    private final XMLStreamReader reader;
    private final List<Runnable> endTasks = new ArrayList<>();
    private final ImportOptions options;

    public NetworkXmlReaderContext(Anonymizer anonymizer, XMLStreamReader reader, ImportOptions options) {
        super(anonymizer);
        this.reader = Objects.requireNonNull(reader);
        this.options = Objects.requireNonNull(options);
    }

    public NetworkXmlReaderContext(Anonymizer anonymizer, XMLStreamReader reader) {
        this(anonymizer, reader, new ImportOptions());
    }

    @Override
    public XMLStreamReader getReader() {
        return reader;
    }

    public List<Runnable> getEndTasks() {
        return endTasks;
    }

    @Override
    public ImportOptions getOptions() {
        return options;
    }
}
