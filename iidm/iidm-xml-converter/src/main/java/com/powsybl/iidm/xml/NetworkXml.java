/**
 * Copyright (c) 2016, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.xml;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.datasource.DataSource;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.exceptions.UncheckedSaxException;
import com.powsybl.commons.exceptions.UncheckedXmlStreamException;
import com.powsybl.commons.extensions.Extension;
import com.powsybl.commons.extensions.ExtensionProviders;
import com.powsybl.commons.extensions.ExtensionXmlSerializer;
import com.powsybl.commons.xml.XmlUtil;
import com.powsybl.iidm.IidmImportExportMode;
import com.powsybl.iidm.anonymizer.Anonymizer;
import com.powsybl.iidm.anonymizer.SimpleAnonymizer;
import com.powsybl.iidm.export.BusFilter;
import com.powsybl.iidm.export.ExportOptions;
import com.powsybl.iidm.import_.ImportOptions;
import com.powsybl.iidm.network.*;
import javanet.staxutils.IndentingXMLStreamWriter;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.stream.*;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static com.powsybl.iidm.xml.IidmXmlConstants.*;

/**
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class NetworkXml {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkXml.class);

    private static final String EXTENSION_CATEGORY_NAME = "network";
    static final String NETWORK_ROOT_ELEMENT_NAME = "network";
    private static final String EXTENSION_ELEMENT_NAME = "extension";
    private static final String IIDM_XSD = "iidm.xsd";
    private static final String CASE_DATE = "caseDate";
    private static final String FORECAST_DISTANCE = "forecastDistance";
    private static final String SOURCE_FORMAT = "sourceFormat";
    private static final String ID = "id";
    private static final String XIIDM = "xiidm";

    // cache XMLOutputFactory to improve performance
    private static final Supplier<XMLOutputFactory> XML_OUTPUT_FACTORY_SUPPLIER = Suppliers.memoize(XMLOutputFactory::newFactory);

    private static final Supplier<XMLInputFactory> XML_INPUT_FACTORY_SUPPLIER = Suppliers.memoize(XMLInputFactory::newInstance);

    private static final Supplier<ExtensionProviders<ExtensionXmlSerializer>> EXTENSIONS_SUPPLIER = Suppliers.memoize(() -> ExtensionProviders.createProvider(ExtensionXmlSerializer.class, EXTENSION_CATEGORY_NAME));

    private NetworkXml() {
    }

    private static XMLStreamWriter createXmlStreamWriter(ExportOptions options, OutputStream os) throws XMLStreamException {
        XMLStreamWriter writer = XML_OUTPUT_FACTORY_SUPPLIER.get().createXMLStreamWriter(os, StandardCharsets.UTF_8.toString());
        if (options.isIndent()) {
            IndentingXMLStreamWriter indentingWriter = new IndentingXMLStreamWriter(writer);
            indentingWriter.setIndent(INDENT);
            writer = indentingWriter;
        }
        return writer;
    }

    private static Set<String> getNetworkExtensions(Network n) {
        Set<String> extensions = new TreeSet<>();
        for (Identifiable<?> identifiable : n.getIdentifiables()) {
            for (Extension<? extends Identifiable<?>> extension : identifiable.getExtensions()) {
                extensions.add(extension.getName());
            }
        }
        return extensions;
    }

    private static void validate(Source xml, List<Source> additionalSchemas) {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Source[] sources = new Source[additionalSchemas.size() + 1];
        sources[0] = new StreamSource(NetworkXml.class.getResourceAsStream("/xsd/" + IIDM_XSD));
        for (int i = 0; i < additionalSchemas.size(); i++) {
            sources[i + 1] = additionalSchemas.get(i);
        }
        try {
            Schema schema = factory.newSchema(sources);
            Validator validator = schema.newValidator();
            validator.validate(xml);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (SAXException e) {
            throw new UncheckedSaxException(e);
        }
    }

    static void validate(InputStream is) {
        validate(new StreamSource(is), Collections.emptyList());
    }

    static void validate(Path file) {
        try (InputStream is = Files.newInputStream(file)) {
            validate(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void validateWithExtensions(InputStream is) {
        List<Source> additionalSchemas = EXTENSIONS_SUPPLIER.get().getProviders().stream()
                .map(e -> new StreamSource(e.getXsdAsStream()))
                .collect(Collectors.toList());
        validate(new StreamSource(is), additionalSchemas);
    }

    static void validateWithExtensions(Path file) {
        try (InputStream is = Files.newInputStream(file)) {
            validateWithExtensions(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeExtensionNamespaces(Network n, ExportOptions options, XMLStreamWriter writer) throws XMLStreamException {
        Set<String> extensionUris = new HashSet<>();
        Set<String> extensionPrefixes = new HashSet<>();
        for (String extensionName : getNetworkExtensions(n)) {
            ExtensionXmlSerializer extensionXmlSerializer = getExtensionXmlSerializer(options, extensionName);
            if (extensionXmlSerializer == null) {
                continue;
            }
            if (extensionUris.contains(extensionXmlSerializer.getNamespaceUri())) {
                throw new PowsyblException("Extension namespace URI collision");
            } else {
                extensionUris.add(extensionXmlSerializer.getNamespaceUri());
            }
            if (extensionPrefixes.contains(extensionXmlSerializer.getNamespacePrefix())) {
                throw new PowsyblException("Extension namespace prefix collision");
            } else {
                extensionPrefixes.add(extensionXmlSerializer.getNamespacePrefix());
            }
            writer.setPrefix(extensionXmlSerializer.getNamespacePrefix(), extensionXmlSerializer.getNamespaceUri());
            writer.writeNamespace(extensionXmlSerializer.getNamespacePrefix(), extensionXmlSerializer.getNamespaceUri());
        }
    }

    private static void writeExtension(Extension<? extends Identifiable<?>> extension, NetworkXmlWriterContext context) throws XMLStreamException {
        XMLStreamWriter writer = context.getExtensionswWriter();
        ExtensionXmlSerializer extensionXmlSerializer = getExtensionXmlSerializer(context.getOptions(),
                extension.getName());

        if (extensionXmlSerializer == null) {
            if (context.getOptions().isThrowExceptionIfExtensionNotFound()) {
                throw new PowsyblException("XmlSerializer for" + extension.getName() + "not found");
            }
            return;
        }
        if (extensionXmlSerializer.hasSubElements()) {
            writer.writeStartElement(extensionXmlSerializer.getNamespaceUri(), extension.getName());
        } else {
            writer.writeEmptyElement(extensionXmlSerializer.getNamespaceUri(), extension.getName());
        }
        extensionXmlSerializer.write(extension, context);
        if (extensionXmlSerializer.hasSubElements()) {
            writer.writeEndElement();
        }
    }

    private static ExtensionXmlSerializer getExtensionXmlSerializer(ExportOptions options, String extensionName) {
        ExtensionXmlSerializer extensionXmlSerializer = options.isThrowExceptionIfExtensionNotFound()
                ? EXTENSIONS_SUPPLIER.get().findProviderOrThrowException(extensionName)
                : EXTENSIONS_SUPPLIER.get().findProvider(extensionName);
        if (extensionXmlSerializer == null) {
            LOGGER.warn("No Extension XML Serializer for {}", extensionName);
        }
        return extensionXmlSerializer;
    }

    public static Map<String, Set<String>> getIdentifiablesPerExtensionType(Network n) {
        Map<String, Set<String>> extensionsPerType = new HashMap<>();
        for (Identifiable<?> identifiable : n.getIdentifiables()) {
            for (Extension<? extends Identifiable<?>> extension : identifiable.getExtensions()) {
                extensionsPerType.computeIfAbsent(extension.getName(), key -> new HashSet<>()).add(identifiable.getId());
            }
        }
        return extensionsPerType;
    }

    private static void writeExtensions(Network n, NetworkXmlWriterContext context, ExportOptions options) throws XMLStreamException {

        for (Identifiable<?> identifiable : n.getIdentifiables()) {
            Collection<? extends Extension<? extends Identifiable<?>>> extensions = identifiable.getExtensions();
            if (!context.isExportedEquipment(identifiable) || extensions.isEmpty()) {
                continue;
            }
            context.getExtensionswWriter().writeStartElement(IIDM_URI, EXTENSION_ELEMENT_NAME);
            context.getExtensionswWriter().writeAttribute(ID, context.getAnonymizer().anonymizeString(identifiable.getId()));
            for (Extension<? extends Identifiable<?>> extension : identifiable.getExtensions()) {
                if (options.withExtension(extension.getName())) {
                    writeExtension(extension, context);
                }
            }
            context.getExtensionswWriter().writeEndElement();
        }
    }

    private static XMLStreamWriter initializeWriter(Network n, OutputStream os, ExportOptions options) throws XMLStreamException {
        XMLStreamWriter writer;
        writer = createXmlStreamWriter(options, os);
        writer.writeStartDocument(StandardCharsets.UTF_8.toString(), "1.0");
        writer.setPrefix(IIDM_PREFIX, IIDM_URI);
        writer.writeStartElement(IIDM_URI, NETWORK_ROOT_ELEMENT_NAME);
        writer.writeNamespace(IIDM_PREFIX, IIDM_URI);
        if (!options.withNoExtension()) {
            writeExtensionNamespaces(n, options, writer);
        }
        writer.writeAttribute(ID, n.getId());
        writer.writeAttribute(CASE_DATE, n.getCaseDate().toString());
        writer.writeAttribute(FORECAST_DISTANCE, Integer.toString(n.getForecastDistance()));
        writer.writeAttribute(SOURCE_FORMAT, n.getSourceFormat());
        return writer;
    }

    private static void writeEndElement(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeEndElement();
        writer.writeEndDocument();
    }

    public static void writeExtensionsInMultipleFile(Network n, NetworkXmlWriterContext context, DataSource dataSource, ExportOptions options) throws XMLStreamException, IOException {
        //here we right one extension type  per file
        Map<String, Set<String>> m = getIdentifiablesPerExtensionType(n);

        for (Map.Entry<String, Set<String>> entry : m.entrySet()) {
            String name = entry.getKey();
            Set<String> ids = entry.getValue();
            if (options.withExtension(name) || options.withAllExtensions()) {
                try (OutputStream os = dataSource.newOutputStream(name, XIIDM, false);
                     BufferedOutputStream bos = new BufferedOutputStream(os)) {
                    XMLStreamWriter writer = initializeWriter(n, bos, options);
                    for (String id : ids) {
                        writer.writeStartElement(IIDM_URI, EXTENSION_ELEMENT_NAME);
                        writer.writeAttribute("id", context.getAnonymizer().anonymizeString(id));
                        context.setExtensionsWriter(writer);
                        writeExtension(n.getIdentifiable(id).getExtensionByName(name), context);
                        writer.writeEndElement();
                    }
                    writeEndElement(writer);
                }
            }
        }
    }

    public static NetworkXmlWriterContext writeBaseNetwork(Network n, OutputStream os, ExportOptions options) throws XMLStreamException {

        // create the  writer of the base file
        final XMLStreamWriter writer = initializeWriter(n, os, options);
        BusFilter filter = BusFilter.create(n, options);
        Anonymizer anonymizer = options.isAnonymized() ? new SimpleAnonymizer() : null;
        NetworkXmlWriterContext context = new NetworkXmlWriterContext(anonymizer, writer, options, filter);
        // Consider the network has been exported so its extensions will be written also
        context.addExportedEquipment(n);

        for (Substation s : n.getSubstations()) {
            SubstationXml.INSTANCE.write(s, null, context);
        }
        for (Line l : n.getLines()) {
            if (!filter.test(l)) {
                continue;
            }
            if (l.isTieLine()) {
                TieLineXml.INSTANCE.write((TieLine) l, n, context);
            } else {
                LineXml.INSTANCE.write(l, n, context);
            }
        }
        for (HvdcLine l : n.getHvdcLines()) {
            if (!filter.test(l.getConverterStation1()) || !filter.test(l.getConverterStation2())) {
                continue;
            }
            HvdcLineXml.INSTANCE.write(l, n, context);
        }
        return context;
    }

    public static Anonymizer write(Network n, ExportOptions options, OutputStream os) {
        try {
            NetworkXmlWriterContext context = writeBaseNetwork(n, os, options);
            // write extensions
            writeExtensions(n, context, options);
            writeEndElement(context.getWriter());
            return context.getAnonymizer();
        } catch (XMLStreamException e) {
            throw new UncheckedXmlStreamException(e);
        }
    }

    public static Anonymizer write(Network n, OutputStream os) {
        return write(n, new ExportOptions(), os);
    }

    public static Anonymizer write(Network n, ExportOptions options, Path xmlFile) {
        try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(xmlFile))) {
            return write(n, options, os);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Anonymizer write(Network n, Path xmlFile) {
        return write(n, new ExportOptions(), xmlFile);
    }

    public static Anonymizer write(Network network, ExportOptions options, DataSource dataSource) throws IOException {
        try (OutputStream osb = dataSource.newOutputStream(null, XIIDM, false);
             BufferedOutputStream bosb = new BufferedOutputStream(osb)) {

            if (options.getMode() == IidmImportExportMode.UNIQUE_FILE) {
                Anonymizer anonymizer = write(network, options, bosb);
                if (options.isAnonymized()) {
                    try (BufferedWriter writer2 = new BufferedWriter(new OutputStreamWriter(dataSource.newOutputStream("_mapping", "csv", false), StandardCharsets.UTF_8))) {
                        anonymizer.write(writer2);
                    }
                }
                return anonymizer;
            }

            NetworkXmlWriterContext context = writeBaseNetwork(network, bosb, options);
            writeEndElement(context.getWriter());

            // write extensions
            if (!options.withNoExtension() && !getNetworkExtensions(network).isEmpty()) {

                if (options.getMode() == IidmImportExportMode.EXTENSIONS_IN_ONE_SEPARATED_FILE) {
                    try (OutputStream ose = dataSource.newOutputStream("-ext", XIIDM, false);
                         BufferedOutputStream bose = new BufferedOutputStream(ose)) {

                        final XMLStreamWriter extensionsWriter = initializeWriter(network, bose, options);
                        context.setExtensionsWriter(extensionsWriter);
                        writeExtensions(network, context, options);
                        writeEndElement(extensionsWriter);
                    }
                } else if (options.getMode() == IidmImportExportMode.ONE_SEPARATED_FILE_PER_EXTENSION_TYPE) {
                    writeExtensionsInMultipleFile(network, context, dataSource, options);
                }
            }

            if (options.isAnonymized()) {
                try (BufferedWriter writer2 = new BufferedWriter(new OutputStreamWriter(dataSource.newOutputStream("_mapping", "csv", false), StandardCharsets.UTF_8))) {
                    context.getAnonymizer().write(writer2);
                }
            }
            return context.getAnonymizer();
        } catch (XMLStreamException e) {
            throw new UncheckedXmlStreamException(e);
        }
    }

    public static Anonymizer writeAndValidate(Network n, Path xmlFile) {
        Anonymizer anonymizer = write(n, xmlFile);
        validateWithExtensions(xmlFile);
        return anonymizer;
    }

    public static Network read(InputStream is) {
        return read(is, new ImportOptions(), null);
    }

    public static Network read(InputStream is, ImportOptions config, Anonymizer anonymizer) {
        try {
            XMLStreamReader reader = XML_INPUT_FACTORY_SUPPLIER.get().createXMLStreamReader(is);
            int state = reader.next();
            while (state == XMLStreamReader.COMMENT) {
                state = reader.next();
            }
            String id = reader.getAttributeValue(null, ID);
            DateTime date = DateTime.parse(reader.getAttributeValue(null, CASE_DATE));
            int forecastDistance = XmlUtil.readOptionalIntegerAttribute(reader, FORECAST_DISTANCE, 0);
            String sourceFormat = reader.getAttributeValue(null, SOURCE_FORMAT);

            Network network = NetworkFactory.create(id, sourceFormat);
            network.setCaseDate(date);
            network.setForecastDistance(forecastDistance);

            NetworkXmlReaderContext context = new NetworkXmlReaderContext(anonymizer, reader, config);

            Set<String> extensionNamesNotFound = new TreeSet<>();

            XmlUtil.readUntilEndElement(NETWORK_ROOT_ELEMENT_NAME, reader, () -> {
                switch (reader.getLocalName()) {
                    case SubstationXml.ROOT_ELEMENT_NAME:
                        SubstationXml.INSTANCE.read(network, context);
                        break;

                    case LineXml.ROOT_ELEMENT_NAME:
                        LineXml.INSTANCE.read(network, context);
                        break;

                    case TieLineXml.ROOT_ELEMENT_NAME:
                        TieLineXml.INSTANCE.read(network, context);
                        break;

                    case HvdcLineXml.ROOT_ELEMENT_NAME:
                        HvdcLineXml.INSTANCE.read(network, context);
                        break;

                    case EXTENSION_ELEMENT_NAME:
                        String id2 = context.getAnonymizer().deanonymizeString(reader.getAttributeValue(null, "id"));
                        Identifiable identifiable = network.getIdentifiable(id2);
                        if (identifiable == null) {
                            throw new PowsyblException("Identifiable " + id2 + " not found");
                        }

                        readExtensions(identifiable, context, extensionNamesNotFound);
                        break;

                    default:
                        throw new AssertionError();
                }
            });
            context.getEndTasks().forEach(Runnable::run);
            return network;
        } catch (XMLStreamException e) {
            throw new UncheckedXmlStreamException(e);
        }
    }

    public static Network read(Path xmlFile) {
        try (InputStream is = Files.newInputStream(xmlFile)) {
            return read(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Network validateAndRead(Path xmlFile) {
        validateWithExtensions(xmlFile);
        return read(xmlFile);
    }


    // To read extensions from multiple extension files
    public static void readExtensions(Network network, ReadOnlyDataSource dataSource, Anonymizer anonymizer, ImportOptions options, String ext) throws IOException {
        options.getExtensions().ifPresent(extensions -> {
            for (String extension : extensions) {
                try (InputStream ise = dataSource.newInputStream(extension + "." + ext)) {
                    readExtensions(network, ise, anonymizer, options);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
    }

    // To read extensions from an extensions file
    public static Network readExtensions(Network network, InputStream ise, Anonymizer anonymizer, ImportOptions options) {
        try {
            XMLStreamReader reader = XML_INPUT_FACTORY_SUPPLIER.get().createXMLStreamReader(ise);
            int state = reader.next();
            while (state == XMLStreamReader.COMMENT) {
                state = reader.next();
            }
            String id = reader.getAttributeValue(null, "id");
            DateTime date = DateTime.parse(reader.getAttributeValue(null, CASE_DATE));

            //verify that the extensions file matches with the same network
            if (!network.getId().equals(id) || !network.getCaseDate().equals(date)) {
                throw new PowsyblException("Extension file do not match with the base file !");
            }

            NetworkXmlReaderContext context = new NetworkXmlReaderContext(anonymizer, reader, options);
            Set<String> extensionNamesNotFound = new TreeSet<>();

            XmlUtil.readUntilEndElement(NETWORK_ROOT_ELEMENT_NAME, reader, () -> {
                if (reader.getLocalName().equals(EXTENSION_ELEMENT_NAME)) {
                    String id2 = context.getAnonymizer().deanonymizeString(reader.getAttributeValue(null, "id"));
                    Identifiable identifiable = network.getIdentifiable(id2);
                    if (identifiable == null) {
                        throw new PowsyblException("Identifiable " + id2 + " not found");
                    }
                    readExtensions(identifiable, context, extensionNamesNotFound);
                } else {
                    throw new AssertionError();
                }
            });
            return network;
        } catch (XMLStreamException e) {
            throw new UncheckedXmlStreamException(e);
        }
    }

    private static void readExtensions(Identifiable identifiable, NetworkXmlReaderContext context,
                                       Set<String> extensionNamesNotFound) throws XMLStreamException {

        XmlUtil.readUntilEndElement(EXTENSION_ELEMENT_NAME, context.getReader(), new XmlUtil.XmlEventHandler() {

            private boolean topLevel = true;

            @Override
            public void onStartElement() throws XMLStreamException {
                if (topLevel) {
                    String extensionName = context.getReader().getLocalName();
                    if (context.getOptions().getExtensions().isPresent() && !context.getOptions().withExtension(extensionName)) {
                        return;
                    }

                    ExtensionXmlSerializer extensionXmlSerializer = EXTENSIONS_SUPPLIER.get().findProvider(extensionName);
                    if (extensionXmlSerializer != null) {
                        Extension<? extends Identifiable<?>> extension = extensionXmlSerializer.read(identifiable, context);
                        identifiable.addExtension(extensionXmlSerializer.getExtensionClass(), extension);
                        topLevel = true;
                    } else {
                        extensionNamesNotFound.add(extensionName);
                        topLevel = false;
                    }
                }
            }
        });

        if (!extensionNamesNotFound.isEmpty()) {
            if (context.getOptions().isThrowExceptionIfExtensionNotFound()) {
                throw new PowsyblException("Extensions " + extensionNamesNotFound + " " +
                        "not found !");
            } else {
                LOGGER.error("Extensions {} not found", extensionNamesNotFound);
            }
        }
    }

    public static void update(Network network, InputStream is) {
        try {
            XMLStreamReader reader = XML_INPUT_FACTORY_SUPPLIER.get().createXMLStreamReader(is);
            reader.next();

            final VoltageLevel[] vl = new VoltageLevel[1];

            XmlUtil.readUntilEndElement(NETWORK_ROOT_ELEMENT_NAME, reader, () -> {

                switch (reader.getLocalName()) {
                    case VoltageLevelXml.ROOT_ELEMENT_NAME:
                        updateVoltageLevel(reader, network, vl);
                        break;

                    case BusXml.ROOT_ELEMENT_NAME:
                        updateBus(reader, vl);
                        break;

                    case GeneratorXml.ROOT_ELEMENT_NAME:
                    case LoadXml.ROOT_ELEMENT_NAME:
                    case ShuntXml.ROOT_ELEMENT_NAME:
                    case DanglingLineXml.ROOT_ELEMENT_NAME:
                    case LccConverterStationXml.ROOT_ELEMENT_NAME:
                    case VscConverterStationXml.ROOT_ELEMENT_NAME:
                        updateInjection(reader, network);
                        break;

                    case LineXml.ROOT_ELEMENT_NAME:
                    case TwoWindingsTransformerXml.ROOT_ELEMENT_NAME:
                        updateBranch(reader, network);
                        break;

                    case HvdcLineXml.ROOT_ELEMENT_NAME:
                        // Nothing to do
                        break;

                    case ThreeWindingsTransformerXml.ROOT_ELEMENT_NAME:
                        throw new AssertionError();

                    default:
                        throw new AssertionError("Unexpected element: " + reader.getLocalName());
                }
            });
        } catch (XMLStreamException e) {
            throw new UncheckedXmlStreamException(e);
        }
    }

    public static void update(Network network, Path xmlFile) {
        try (InputStream is = Files.newInputStream(xmlFile)) {
            update(network, is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void updateVoltageLevel(XMLStreamReader reader, Network network, VoltageLevel[] vl) {
        String id = reader.getAttributeValue(null, "id");
        vl[0] = network.getVoltageLevel(id);
        if (vl[0] == null) {
            throw new PowsyblException("Voltage level '" + id + "' not found");
        }
    }

    private static void updateBus(XMLStreamReader reader, VoltageLevel[] vl) {
        String id = reader.getAttributeValue(null, "id");
        double v = XmlUtil.readDoubleAttribute(reader, "v");
        double angle = XmlUtil.readDoubleAttribute(reader, "angle");
        Bus b = vl[0].getBusBreakerView().getBus(id);
        if (b == null) {
            b = vl[0].getBusView().getBus(id);
        }
        b.setV(v > 0 ? v : Double.NaN).setAngle(angle);
    }

    private static void updateInjection(XMLStreamReader reader, Network network) {
        String id = reader.getAttributeValue(null, "id");
        double p = XmlUtil.readOptionalDoubleAttribute(reader, "p");
        double q = XmlUtil.readOptionalDoubleAttribute(reader, "q");
        Injection inj = (Injection) network.getIdentifiable(id);
        inj.getTerminal().setP(p).setQ(q);
    }

    private static void updateBranch(XMLStreamReader reader, Network network) {
        String id = reader.getAttributeValue(null, "id");
        double p1 = XmlUtil.readOptionalDoubleAttribute(reader, "p1");
        double q1 = XmlUtil.readOptionalDoubleAttribute(reader, "q1");
        double p2 = XmlUtil.readOptionalDoubleAttribute(reader, "p2");
        double q2 = XmlUtil.readOptionalDoubleAttribute(reader, "q2");
        Branch branch = (Branch) network.getIdentifiable(id);
        branch.getTerminal1().setP(p1).setQ(q1);
        branch.getTerminal2().setP(p2).setQ(q2);
    }

    public static byte[] gzip(Network network) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(bos)) {
            write(network, gzos);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bos.toByteArray();
    }

    public static Network gunzip(byte[] networkXmlGz) {
        try (InputStream is = new GZIPInputStream(new ByteArrayInputStream(networkXmlGz))) {
            return read(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Deep copy of the network using XML converter.
     * @param network the network to copy
     * @return the copy of the network
     */
    public static Network copy(Network network) {
        return copy(network, ForkJoinPool.commonPool());
    }

    public static Network copy(Network network, ExecutorService executor) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(executor);
        PipedOutputStream pos = new PipedOutputStream();
        try (InputStream is = new PipedInputStream(pos)) {
            executor.execute(() -> {
                try {
                    write(network, pos);
                } catch (Exception t) {
                    LOGGER.error(t.toString(), t);
                } finally {
                    try {
                        pos.close();
                    } catch (IOException e) {
                        LOGGER.error(e.toString(), e);
                    }
                }
            });
            return read(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
