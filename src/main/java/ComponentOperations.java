import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ComponentOperations {

    private static final DateTimeFormatter FOLDER_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter LOG_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public record ExportResult(String componentId, Path zipPath) {
    }

    public static final class EmptyPolicyChoice {
        public enum Action { ADD, CANCEL }

        private final Action action;
        private final String componentId;
        private final boolean enabled;
        private final boolean applyToAll;

        private EmptyPolicyChoice(Action action, String componentId, boolean enabled, boolean applyToAll) {
            this.action = action;
            this.componentId = componentId;
            this.enabled = enabled;
            this.applyToAll = applyToAll;
        }

        public static EmptyPolicyChoice cancel() {
            return new EmptyPolicyChoice(Action.CANCEL, null, false, false);
        }

        public static EmptyPolicyChoice add(String componentId, boolean enabled, boolean applyToAll) {
            return new EmptyPolicyChoice(Action.ADD, componentId, enabled, applyToAll);
        }

        public Action action() {
            return action;
        }

        public String componentId() {
            return componentId;
        }

        public boolean enabled() {
            return enabled;
        }

        public boolean applyToAll() {
            return applyToAll;
        }
    }

    public record RemovalResult(
            boolean cancelled,
            Path outputPolicies,
            Path outputPvConfiguration,
            Path changelog,
            int totalRemovedAssignments,
            int removedDefinitions,
            Map<String, Integer> removedPerComponent,
            List<String> emptyPoliciesFixed) {

        static RemovalResult cancelledResult() {
            return new RemovalResult(true, null, null, null, 0, 0, Map.of(), List.of());
        }
    }

    private record PolicyEditResult(
            boolean cancelled,
            int totalRemoved,
            Map<String, Integer> removedPerComponent,
            List<String> emptyPoliciesFixed) {

        static PolicyEditResult cancelledResult() {
            return new PolicyEditResult(true, 0, Map.of(), List.of());
        }
    }

    // ----------------------------------------------------------------------------------------- Export

    public ExportResult exportConnectionComponent(String pvConfigurationPath, String componentId, Path destinationRoot) throws Exception {
        if (componentId == null || componentId.isBlank()) {
            throw new IllegalArgumentException("Connection component id is required.");
        }

        Document doc = loadDocument(pvConfigurationPath);
        Element component = findComponentDefinition(doc, componentId.trim());
        if (component == null) {
            throw new IllegalArgumentException("Connection component not found in PVConfiguration.xml: " + componentId);
        }

        String xml = serializeElement(component);

        String safeId = sanitizeFileName(componentId);
        Path componentFolder = destinationRoot.resolve(safeId);
        Files.createDirectories(componentFolder);

        Path zipPath = componentFolder.resolve("PSM-" + safeId + ".zip");
        writeZip(zipPath, "CC-" + safeId + ".xml", xml);

        return new ExportResult(componentId, zipPath);
    }


    private void writeZip(Path zipPath, String entryName, String content) throws IOException {
        Path parent = zipPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    // ----------------------------------------------------------------------------- Unlink (Policies only)

    public RemovalResult unlinkConnectionComponents(
            String policiesPath,
            Collection<String> componentIds,
            Path outputRoot,
            String sourceLabel,
            String sourceFolder,
            Function<String, EmptyPolicyChoice> emptyPolicyResolver) throws Exception {

        Set<String> removeSet = toRemoveSet(componentIds);

        Document policiesDoc = loadDocument(policiesPath);
        PolicyEditResult edit = applyPolicyRemovals(policiesDoc, removeSet, emptyPolicyResolver);
        if (edit.cancelled()) {
            return RemovalResult.cancelledResult();
        }

        Path folder = createOutputFolder(outputRoot, sourceLabel);
        Path outputPolicies = folder.resolve("Policies.xml");
        writeDocument(policiesDoc, outputPolicies);

        Path changelog = folder.resolve("changelog.txt");
        writeChangelog(changelog, "Unlink (Policies.xml only)", sourceLabel, sourceFolder, removeSet, edit, 0);

        return new RemovalResult(false, outputPolicies, null, changelog,
                edit.totalRemoved(), 0, edit.removedPerComponent(), edit.emptyPoliciesFixed());
    }

    // ------------------------------------------------------------------ Remove (Policies + PVConfiguration)

    public RemovalResult removeConnectionComponents(
            String policiesPath,
            String pvConfigurationPath,
            Collection<String> componentIds,
            Path outputRoot,
            String sourceLabel,
            String sourceFolder,
            Function<String, EmptyPolicyChoice> emptyPolicyResolver) throws Exception {

        Set<String> removeSet = toRemoveSet(componentIds);

        Document policiesDoc = loadDocument(policiesPath);
        PolicyEditResult edit = applyPolicyRemovals(policiesDoc, removeSet, emptyPolicyResolver);
        if (edit.cancelled()) {
            return RemovalResult.cancelledResult();
        }

        Document pvDoc = loadDocument(pvConfigurationPath);
        int removedDefinitions = removeComponentDefinitions(pvDoc, removeSet);

        Path folder = createOutputFolder(outputRoot, sourceLabel);

        Path outputPolicies = folder.resolve("Policies.xml");
        writeDocument(policiesDoc, outputPolicies);

        Path outputPvConfiguration = folder.resolve("PVConfiguration.xml");
        writeDocument(pvDoc, outputPvConfiguration);

        Path changelog = folder.resolve("changelog.txt");
        writeChangelog(changelog, "Remove (Policies.xml + PVConfiguration.xml)", sourceLabel, sourceFolder,
                removeSet, edit, removedDefinitions);

        return new RemovalResult(false, outputPolicies, outputPvConfiguration, changelog,
                edit.totalRemoved(), removedDefinitions, edit.removedPerComponent(), edit.emptyPoliciesFixed());
    }

    // ---------------------------------------------------------------------------------- Policies editing

    private PolicyEditResult applyPolicyRemovals(
            Document policiesDoc,
            Set<String> removeSet,
            Function<String, EmptyPolicyChoice> emptyPolicyResolver) {

        EmptyPolicyChoice cachedChoice = null;
        int totalRemoved = 0;
        Map<String, Integer> removedPerComponent = new LinkedHashMap<>();
        List<String> emptyPoliciesFixed = new ArrayList<>();

        for (Element policy : toElementList(policiesDoc.getElementsByTagName("Policy"))) {
            Element ccParent = firstChildElement(policy, "ConnectionComponents");
            if (ccParent == null) {
                continue;
            }

            int removedHere = 0;
            for (Element cc : childElements(ccParent, "ConnectionComponent")) {
                String id = cc.getAttribute("Id").trim();
                if (removeSet.contains(id)) {
                    removeWithLeadingWhitespace(cc);
                    removedHere++;
                    totalRemoved++;
                    removedPerComponent.merge(id, 1, Integer::sum);
                }
            }

            if (removedHere == 0) {
                continue;
            }

            if (childElements(ccParent, "ConnectionComponent").isEmpty()) {
                String policyId = policy.getAttribute("ID");
                EmptyPolicyChoice choice = cachedChoice;
                if (choice == null) {
                    choice = emptyPolicyResolver.apply(policyId);
                    if (choice == null || choice.action() == EmptyPolicyChoice.Action.CANCEL) {
                        return PolicyEditResult.cancelledResult();
                    }
                    if (choice.applyToAll()) {
                        cachedChoice = choice;
                    }
                }

                Element replacement = policiesDoc.createElement("ConnectionComponent");
                replacement.setAttribute("Id", choice.componentId());
                replacement.setAttribute("Enable", choice.enabled() ? "Yes" : "No");
                insertChildKeepingIndent(ccParent, replacement);
                emptyPoliciesFixed.add(policyId + " -> " + choice.componentId()
                        + " (" + (choice.enabled() ? "enabled" : "disabled") + ")");
            }
        }

        return new PolicyEditResult(false, totalRemoved, removedPerComponent, emptyPoliciesFixed);
    }

    // ------------------------------------------------------------------------ PVConfiguration editing

    private int removeComponentDefinitions(Document pvDoc, Set<String> removeSet) {
        int removed = 0;
        for (Element component : toElementList(pvDoc.getElementsByTagName("ConnectionComponent"))) {
            Node parent = component.getParentNode();
            boolean isDefinition = parent != null
                    && parent.getNodeType() == Node.ELEMENT_NODE
                    && "ConnectionComponents".equalsIgnoreCase(((Element) parent).getTagName());
            if (isDefinition && removeSet.contains(component.getAttribute("Id").trim())) {
                removeWithLeadingWhitespace(component);
                removed++;
            }
        }
        return removed;
    }

    private Element findComponentDefinition(Document doc, String componentId) {
        for (Element component : toElementList(doc.getElementsByTagName("ConnectionComponent"))) {
            if (componentId.equals(component.getAttribute("Id").trim())) {
                return component;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------- Changelog

    private Path createOutputFolder(Path outputRoot, String sourceLabel) throws IOException {
        String timestamp = LocalDateTime.now().format(FOLDER_TIMESTAMP);
        Path folder = outputRoot.resolve(timestamp + "_" + sanitizeFileName(sourceLabel));
        Files.createDirectories(folder);
        return folder;
    }

    private void writeChangelog(
            Path changelog,
            String operation,
            String sourceLabel,
            String sourceFolder,
            Set<String> requestedRemovals,
            PolicyEditResult edit,
            int removedDefinitions) throws IOException {

        StringBuilder sb = new StringBuilder();
        sb.append("CyberArk VaultOps - Connection Component Change\n");
        sb.append("Operation : ").append(operation).append('\n');
        sb.append("Timestamp : ").append(LocalDateTime.now().format(LOG_TIMESTAMP)).append('\n');
        sb.append("Source    : ").append(sourceLabel == null || sourceLabel.isBlank() ? "Unknown" : sourceLabel);
        if (sourceFolder != null && !sourceFolder.isBlank()) {
            sb.append("  [").append(sourceFolder).append(']');
        }
        sb.append('\n');
        sb.append("Requested components : ").append(String.join(", ", requestedRemovals)).append('\n');
        sb.append("Policy assignments removed : ").append(edit.totalRemoved()).append('\n');
        sb.append("PVConfiguration definitions removed : ").append(removedDefinitions).append('\n');
        sb.append('\n');

        sb.append("Removed per component:\n");
        if (edit.removedPerComponent().isEmpty()) {
            sb.append("  (none matched any policy)\n");
        } else {
            for (Map.Entry<String, Integer> entry : edit.removedPerComponent().entrySet()) {
                sb.append("  - ").append(entry.getKey()).append(" : ").append(entry.getValue()).append(" assignment(s)\n");
            }
        }
        sb.append('\n');

        sb.append("Policies that received a default component (would otherwise be empty):\n");
        if (edit.emptyPoliciesFixed().isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (String fixed : edit.emptyPoliciesFixed()) {
                sb.append("  - ").append(fixed).append('\n');
            }
        }

        Files.writeString(changelog, sb.toString(), StandardCharsets.UTF_8);
    }

    // --------------------------------------------------------------------------------------- DOM helpers

    private void removeWithLeadingWhitespace(Element element) {
        Node parent = element.getParentNode();
        if (parent == null) {
            return;
        }
        Node previous = element.getPreviousSibling();
        if (previous != null && previous.getNodeType() == Node.TEXT_NODE && previous.getTextContent().isBlank()) {
            parent.removeChild(previous);
        }
        parent.removeChild(element);
    }

    private void insertChildKeepingIndent(Element parent, Element child) {
        Document doc = parent.getOwnerDocument();
        Node trailing = parent.getLastChild();
        Node indentNode = doc.createTextNode("\n            ");
        if (trailing != null && trailing.getNodeType() == Node.TEXT_NODE && trailing.getTextContent().isBlank()) {
            parent.insertBefore(indentNode, trailing);
            parent.insertBefore(child, trailing);
        } else {
            parent.appendChild(indentNode);
            parent.appendChild(child);
            parent.appendChild(doc.createTextNode("\n          "));
        }
    }

    private Set<String> toRemoveSet(Collection<String> componentIds) {
        Set<String> removeSet = new LinkedHashSet<>();
        if (componentIds != null) {
            for (String id : componentIds) {
                if (id != null && !id.isBlank()) {
                    removeSet.add(id.trim());
                }
            }
        }
        if (removeSet.isEmpty()) {
            throw new IllegalArgumentException("No connection components selected.");
        }
        return removeSet;
    }

    private static List<Element> toElementList(NodeList nodes) {
        List<Element> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private static Element firstChildElement(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && name.equalsIgnoreCase(((Element) node).getTagName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private static List<Element> childElements(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && name.equalsIgnoreCase(((Element) node).getTagName())) {
                result.add((Element) node);
            }
        }
        return result;
    }

    // ---------------------------------------------------------------------------------------- XML IO

    private Document loadDocument(String xmlPath) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        dbf.setIgnoringComments(false);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(new File(xmlPath));
    }

    private String serializeElement(Element element) throws Exception {
        Transformer transformer = newSecureTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(element), new StreamResult(writer));
        return writer.toString();
    }

    private void writeDocument(Document doc, Path path) throws Exception {
        Transformer transformer = newSecureTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        try (OutputStream out = Files.newOutputStream(path)) {
            transformer.transform(new DOMSource(doc), new StreamResult(out));
        }
    }

    private Transformer newSecureTransformer() throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        try {
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        } catch (IllegalArgumentException ignored) {
            // Attribute not supported by this implementation; safe to ignore.
        }
        try {
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        } catch (IllegalArgumentException ignored) {
            // Attribute not supported by this implementation; safe to ignore.
        }
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        return transformer;
    }

    private static String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "unnamed";
        }
        return name.trim().replaceAll("[^A-Za-z0-9._-]", "_");
    }
}

