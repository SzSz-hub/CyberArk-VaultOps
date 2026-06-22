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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ComponentOperations {

    private static final DateTimeFormatter FOLDER_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter LOG_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MAX_IMPORT_ENTRY_BYTES = 16L * 1024 * 1024;

    public record ExportResult(String componentId, Path zipPath) {
    }

    public record OrderResult(
            Path outputFolder,
            Path outputPvConfiguration,
            Path outputPolicies,
            Path changelog,
            int pvReordered,
            int policiesReordered) {
    }

    public record ImportResult(
            boolean imported,
            Path outputFolder,
            Path outputPvConfiguration,
            Path changelog,
            List<String> importedIds,
            List<String> skipped) {
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

    public byte[] packageConnectionComponent(String pvConfigurationPath, String componentId) throws Exception {
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

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(buffer)) {
            zos.putNextEntry(new ZipEntry("CC-" + safeId + ".xml"));
            zos.write(xml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return buffer.toByteArray();
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

    // ----------------------------------------------------------------------------------- Order (sort)

    // A single orderable list: either the PVConfiguration definitions ("PVCONFIG") or one policy's
    // assigned components ("POLICY:<policyId>"). componentIds holds the desired order of component Ids;
    // sortByDisplayName controls the auto-sort key (false = by Id, true = by DisplayName).
    public record OrderScope(String key, String label, List<String> componentIds, boolean sortByDisplayName) {
    }

    public OrderResult applyComponentOrder(
            String pvConfigurationPath,
            String policiesPath,
            List<String> pvDefinitionOrder,
            Map<String, List<String>> policyOrders,
            Path outputRoot,
            String sourceLabel,
            String sourceFolder) throws Exception {

        // PVConfiguration.xml: reorder the ConnectionComponent definitions (global list, by Id).
        Document pvDoc = loadDocument(pvConfigurationPath);
        int pvReordered = 0;
        if (pvDefinitionOrder != null && !pvDefinitionOrder.isEmpty()) {
            Element container = findConnectionComponentsContainer(pvDoc);
            if (container != null) {
                List<Element> definitions = childElements(container, "ConnectionComponent");
                if (definitions.size() > 1) {
                    reorderChildElements(container, "ConnectionComponent",
                            orderElementsByIdList(definitions, pvDefinitionOrder));
                    pvReordered = definitions.size();
                }
            }
        }

        // Policies.xml: reorder the assigned ConnectionComponent references inside each chosen policy.
        Document policiesDoc = loadDocument(policiesPath);
        int policiesReordered = 0;
        if (policyOrders != null && !policyOrders.isEmpty()) {
            for (Element policy : toElementList(policiesDoc.getElementsByTagName("Policy"))) {
                List<String> order = policyOrders.get(policy.getAttribute("ID").trim());
                if (order == null || order.isEmpty()) {
                    continue;
                }
                Element ccParent = firstChildElement(policy, "ConnectionComponents");
                if (ccParent == null) {
                    continue;
                }
                List<Element> refs = childElements(ccParent, "ConnectionComponent");
                if (refs.size() <= 1) {
                    continue;
                }
                reorderChildElements(ccParent, "ConnectionComponent", orderElementsByIdList(refs, order));
                policiesReordered++;
            }
        }

        assertNoDuplicateDefinitions(pvDoc);

        Path folder = createOutputFolder(outputRoot, sourceLabel);
        Path outputPv = folder.resolve("PVConfiguration.xml");
        writeDocument(pvDoc, outputPv);
        Path outputPolicies = folder.resolve("Policies.xml");
        writeDocument(policiesDoc, outputPolicies);
        Path changelog = folder.resolve("changelog.txt");
        writeOrderChangelog(changelog, sourceLabel, sourceFolder, pvReordered, policiesReordered);

        return new OrderResult(folder, outputPv, outputPolicies, changelog, pvReordered, policiesReordered);
    }

    // -------------------------------------------------------------------------------- Invariant checks

    private void assertRemovalComplete(Document policiesDoc, Document pvDoc, Set<String> removeSet) {
        for (Element policy : toElementList(policiesDoc.getElementsByTagName("Policy"))) {
            Element ccParent = firstChildElement(policy, "ConnectionComponents");
            if (ccParent == null) {
                continue;
            }
            List<Element> refs = childElements(ccParent, "ConnectionComponent");
            if (refs.isEmpty()) {
                throw new IllegalStateException(
                        "Policy '" + policy.getAttribute("ID").trim() + "' would be left without any connection component.");
            }
            for (Element ref : refs) {
                if (removeSet.contains(ref.getAttribute("Id").trim())) {
                    throw new IllegalStateException("Removed component is still referenced by policy '"
                            + policy.getAttribute("ID").trim() + "': " + ref.getAttribute("Id").trim());
                }
            }
        }
        if (pvDoc != null) {
            Element container = findConnectionComponentsContainer(pvDoc);
            if (container != null) {
                for (Element def : childElements(container, "ConnectionComponent")) {
                    if (removeSet.contains(def.getAttribute("Id").trim())) {
                        throw new IllegalStateException(
                                "Removed component definition is still present: " + def.getAttribute("Id").trim());
                    }
                }
            }
        }
    }

    private void assertNoDuplicateDefinitions(Document pvDoc) {
        Element container = findConnectionComponentsContainer(pvDoc);
        if (container == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (Element def : childElements(container, "ConnectionComponent")) {
            String id = def.getAttribute("Id").trim();
            if (id.isBlank()) {
                throw new IllegalStateException("PVConfiguration.xml contains a ConnectionComponent without an Id.");
            }
            if (!seen.add(id)) {
                throw new IllegalStateException("Duplicate ConnectionComponent definition: " + id);
            }
        }
    }

    private List<Element> orderElementsByIdList(List<Element> elements, List<String> idOrder) {
        Map<String, List<Element>> byId = new LinkedHashMap<>();
        for (Element e : elements) {
            byId.computeIfAbsent(e.getAttribute("Id").trim(), k -> new ArrayList<>()).add(e);
        }

        Set<Element> used = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Element> result = new ArrayList<>();
        if (idOrder != null) {
            for (String id : idOrder) {
                List<Element> matches = byId.get(id == null ? "" : id.trim());
                if (matches == null) {
                    continue;
                }
                for (Element e : matches) {
                    if (used.add(e)) {
                        result.add(e);
                        break;
                    }
                }
            }
        }

        // Any element not named in idOrder keeps its original relative position at the end.
        for (Element e : elements) {
            if (!used.contains(e)) {
                result.add(e);
            }
        }
        return result;
    }


    // --------------------------------------------------------------------------------- Import (PSM zip)

    public ImportResult importConnectionComponents(
            String pvConfigurationPath,
            List<Path> zipFiles,
            Path outputRoot,
            String sourceLabel,
            String sourceFolder) throws Exception {

        if (zipFiles == null || zipFiles.isEmpty()) {
            throw new IllegalArgumentException("No import files selected.");
        }

        Document pvDoc = loadDocument(pvConfigurationPath);
        Element container = findConnectionComponentsContainer(pvDoc);
        if (container == null) {
            throw new IllegalStateException("No <ConnectionComponents> section found in PVConfiguration.xml.");
        }

        Set<String> existingIds = new HashSet<>();
        for (Element def : childElements(container, "ConnectionComponent")) {
            existingIds.add(def.getAttribute("Id").trim());
        }

        List<String> imported = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (Path zip : zipFiles) {
            String label = zip.getFileName() == null ? zip.toString() : zip.getFileName().toString();
            Element cc;
            try {
                cc = readConnectionComponentFromZip(zip);
            } catch (Exception e) {
                skipped.add(label + " (" + e.getMessage() + ")");
                continue;
            }
            if (cc == null) {
                skipped.add(label + " (no ConnectionComponent XML found)");
                continue;
            }
            String id = cc.getAttribute("Id").trim();
            if (id.isBlank()) {
                skipped.add(label + " (ConnectionComponent has no Id)");
                continue;
            }
            if (existingIds.contains(id)) {
                skipped.add(id + " (already exists in PVConfiguration.xml)");
                continue;
            }
            Element importedElement = (Element) pvDoc.importNode(cc, true);
            insertChildKeepingIndent(container, importedElement);
            existingIds.add(id);
            imported.add(id);
        }

        if (imported.isEmpty()) {
            return new ImportResult(false, null, null, null, List.of(), skipped);
        }

        assertNoDuplicateDefinitions(pvDoc);

        Path folder = createOutputFolder(outputRoot, sourceLabel);
        Path outputPv = folder.resolve("PVConfiguration.xml");
        writeDocument(pvDoc, outputPv);
        Path changelog = folder.resolve("changelog.txt");
        writeImportChangelog(changelog, sourceLabel, sourceFolder, imported, skipped);

        return new ImportResult(true, folder, outputPv, changelog, imported, skipped);
    }

    private Element readConnectionComponentFromZip(Path zipPath) throws Exception {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".xml")) {
                    continue;
                }
                if (entry.getSize() > MAX_IMPORT_ENTRY_BYTES) {
                    throw new IOException("XML entry exceeds the " + MAX_IMPORT_ENTRY_BYTES + "-byte import limit: " + entry.getName());
                }
                try (InputStream in = new BoundedInputStream(zipFile.getInputStream(entry), MAX_IMPORT_ENTRY_BYTES)) {
                    Document doc = loadDocumentFromStream(in);
                    Element root = doc.getDocumentElement();
                    if (root == null) {
                        continue;
                    }
                    if ("ConnectionComponent".equalsIgnoreCase(root.getTagName())) {
                        return root;
                    }
                    List<Element> nested = toElementList(doc.getElementsByTagName("ConnectionComponent"));
                    if (!nested.isEmpty()) {
                        return nested.get(0);
                    }
                } catch (Exception ignored) {
                    // Not a parseable / relevant XML entry; keep scanning the archive.
                }
            }
        }
        return null;
    }

    private Element findConnectionComponentsContainer(Document doc) {
        List<Element> containers = toElementList(doc.getElementsByTagName("ConnectionComponents"));
        for (Element container : containers) {
            if (!childElements(container, "ConnectionComponent").isEmpty()) {
                return container;
            }
        }
        return containers.isEmpty() ? null : containers.get(0);
    }

    private void reorderChildElements(Element parent, String childName, List<Element> newOrder) {
        List<Element> current = childElements(parent, childName);
        if (current.size() <= 1) {
            return;
        }

        Node anchor = current.get(current.size() - 1).getNextSibling();
        List<Node> whitespacePool = new ArrayList<>();
        for (Element el : current) {
            Node previous = el.getPreviousSibling();
            if (previous != null && previous.getNodeType() == Node.TEXT_NODE && previous.getTextContent().isBlank()) {
                whitespacePool.add(previous);
                parent.removeChild(previous);
            }
            parent.removeChild(el);
        }

        Document doc = parent.getOwnerDocument();
        Node template = whitespacePool.isEmpty() ? null : whitespacePool.get(0);
        int whitespaceIndex = 0;
        for (Element el : newOrder) {
            Node whitespace;
            if (whitespaceIndex < whitespacePool.size()) {
                whitespace = whitespacePool.get(whitespaceIndex++);
            } else if (template != null) {
                whitespace = template.cloneNode(true);
            } else {
                whitespace = doc.createTextNode("\n            ");
            }
            if (anchor != null) {
                parent.insertBefore(whitespace, anchor);
                parent.insertBefore(el, anchor);
            } else {
                parent.appendChild(whitespace);
                parent.appendChild(el);
            }
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

        assertRemovalComplete(policiesDoc, null, removeSet);

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

        assertRemovalComplete(policiesDoc, pvDoc, removeSet);

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

                String replacementId = choice.componentId() == null ? "" : choice.componentId().trim();
                if (replacementId.isBlank()) {
                    return PolicyEditResult.cancelledResult();
                }
                if (removeSet.contains(replacementId)) {
                    throw new IllegalArgumentException(
                            "Replacement component '" + replacementId + "' is part of the removal set and cannot be reused.");
                }

                Element replacement = policiesDoc.createElement("ConnectionComponent");
                replacement.setAttribute("Id", replacementId);
                replacement.setAttribute("Enable", choice.enabled() ? "Yes" : "No");
                insertChildKeepingIndent(ccParent, replacement);
                emptyPoliciesFixed.add(policyId + " -> " + replacementId
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
            Node parent = component.getParentNode();
            boolean isDefinition = parent != null
                    && parent.getNodeType() == Node.ELEMENT_NODE
                    && "ConnectionComponents".equalsIgnoreCase(((Element) parent).getTagName());
            if (isDefinition && componentId.equals(component.getAttribute("Id").trim())) {
                return component;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------- Changelog

    private Path createOutputFolder(Path outputRoot, String sourceLabel) throws IOException {
        String base = LocalDateTime.now().format(FOLDER_TIMESTAMP) + "_" + sanitizeFileName(sourceLabel);
        Files.createDirectories(outputRoot);
        for (int suffix = 0; ; suffix++) {
            Path folder = outputRoot.resolve(suffix == 0 ? base : base + "_" + suffix);
            try {
                return Files.createDirectory(folder);
            } catch (java.nio.file.FileAlreadyExistsException existing) {
                // Two operations within the same second collided; disambiguate with the next suffix.
            }
        }
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

    private void writeOrderChangelog(
            Path changelog,
            String sourceLabel,
            String sourceFolder,
            int pvReordered,
            int policiesReordered) throws IOException {

        StringBuilder sb = new StringBuilder();
        sb.append("CyberArk VaultOps - Connection Component Change\n");
        sb.append("Operation : Order (sort connection components)\n");
        sb.append("Timestamp : ").append(LocalDateTime.now().format(LOG_TIMESTAMP)).append('\n');
        sb.append("Source    : ").append(sourceLabel == null || sourceLabel.isBlank() ? "Unknown" : sourceLabel);
        if (sourceFolder != null && !sourceFolder.isBlank()) {
            sb.append("  [").append(sourceFolder).append(']');
        }
        sb.append('\n');
        sb.append("PVConfiguration.xml connection components ordered : ").append(pvReordered).append(" (by Id)\n");
        sb.append("Policies.xml policy blocks reordered : ").append(policiesReordered).append(" (assigned components, by DisplayName)\n");

        Files.writeString(changelog, sb.toString(), StandardCharsets.UTF_8);
    }

    private void writeImportChangelog(
            Path changelog,
            String sourceLabel,
            String sourceFolder,
            List<String> imported,
            List<String> skipped) throws IOException {

        StringBuilder sb = new StringBuilder();
        sb.append("CyberArk VaultOps - Connection Component Change\n");
        sb.append("Operation : Import PSM Connection Component\n");
        sb.append("Timestamp : ").append(LocalDateTime.now().format(LOG_TIMESTAMP)).append('\n');
        sb.append("Source    : ").append(sourceLabel == null || sourceLabel.isBlank() ? "Unknown" : sourceLabel);
        if (sourceFolder != null && !sourceFolder.isBlank()) {
            sb.append("  [").append(sourceFolder).append(']');
        }
        sb.append('\n');
        sb.append("Imported into PVConfiguration.xml : ").append(imported.size()).append('\n');
        sb.append('\n');

        sb.append("Imported components:\n");
        if (imported.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (String id : imported) {
                sb.append("  - ").append(id).append('\n');
            }
        }
        sb.append('\n');

        sb.append("Skipped:\n");
        if (skipped.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (String item : skipped) {
                sb.append("  - ").append(item).append('\n');
            }
        }

        Files.writeString(changelog, sb.toString(), StandardCharsets.UTF_8);
    }

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
        DocumentBuilder db = secureDocumentBuilderFactory().newDocumentBuilder();
        return db.parse(new File(xmlPath));
    }

    private Document loadDocumentFromStream(InputStream in) throws Exception {
        DocumentBuilder db = secureDocumentBuilderFactory().newDocumentBuilder();
        return db.parse(in);
    }

    private DocumentBuilderFactory secureDocumentBuilderFactory() throws Exception {
        return Parser.newSecureDocumentBuilderFactory(false);
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
        Path parent = path.getParent();
        Path tempFile = parent == null
                ? Files.createTempFile("vaultops", ".xml.tmp")
                : Files.createTempFile(parent, "vaultops", ".xml.tmp");
        try {
            try (OutputStream out = Files.newOutputStream(tempFile)) {
                transformer.transform(new DOMSource(doc), new StreamResult(out));
            }
            try {
                Files.move(tempFile, path, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempFile);
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

    private static final class BoundedInputStream extends InputStream {
        private final InputStream delegate;
        private final long limit;
        private long read;

        private BoundedInputStream(InputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value != -1) {
                advance(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = delegate.read(buffer, offset, length);
            if (count > 0) {
                advance(count);
            }
            return count;
        }

        private void advance(long count) throws IOException {
            read += count;
            if (read > limit) {
                throw new IOException("Decompressed XML exceeds the " + limit + "-byte import limit.");
            }
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}