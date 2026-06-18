import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PoliciesParser extends Parser {
    public record PolicyEntry(
            String platformId,
            String platformEnabled,
            String policyId,
            String policyName,
            String componentAssigned,
            String hasOverrides,
            String assignedComponents,
            XmlNode details) {
    }

    public record ComponentAssignmentEntry(
            String platformId,
            String policyId,
            String componentId,
            String componentEnabled,
            String hasOverrides) {
    }

    public record usageEntry(
            String usageId,
            String platformBaseId,
            String platformBaseProtocol,
            String platformBaseType,
            Integer policyCount,
            List<XmlNode> children) {
    }

    public record TargetEntry(String effectiveAddress, String sourceAddress, String alteredAddress, String platformId, String policyId) {
    }

    public record AlteredAddressEntry(String address, Integer count) {
    }

    public record TargetDetailEntry(String platformId, String customComponent) {
    }

    public record UsagePolicyEntry(
            String policyId,
            String platformBaseId,
            String componentIds,
            String hasOverrides) {
    }

    public List<PolicyEntry> getPolicies(String policiesPath) throws Exception {
        Document doc = loadDocument(policiesPath);
        NodeList policyNodes = doc.getElementsByTagName("Policy");
        List<PolicyEntry> entries = new ArrayList<>();

        for (int i = 0; i < policyNodes.getLength(); i++) {
            Node node = policyNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element policy = (Element) node;
            String policyId = attr(policy, "ID");
            String platformId = attr(policy, "PlatformBaseID");
            if (platformId.isBlank()) {
                platformId = findDeviceName(policy);
            }

            String platformEnabled = boolLabel(attr(policy, "Enabled"));

            List<Element> components = listConnectionComponents(policy);
            List<String> componentIds = new ArrayList<>();
            boolean hasOverride = false;
            for (Element component : components) {
                String componentId = attr(component, "Id");
                if (!componentId.isBlank()) {
                    componentIds.add(componentId);
                }
                if (hasOverrides(component)) {
                    hasOverride = true;
                }
            }

            entries.add(new PolicyEntry(
                    platformId,
                    platformEnabled,
                    policyId,
                    policyId,
                    componentIds.isEmpty() ? "No" : "Yes",
                    hasOverride ? "Yes" : "No",
                    String.join(", ", componentIds),
                    parseElementTree(policy)
            ));
        }

        return entries;
    }

    public List<ComponentAssignmentEntry> getAssignmentsForConnectionComponent(String policiesPath, String connectionComponentId) throws Exception {
        String targetComponentId = connectionComponentId == null ? "" : connectionComponentId.trim();
        if (targetComponentId.isBlank()) {
            return List.of();
        }

        Document doc = loadDocument(policiesPath);
        NodeList policyNodes = doc.getElementsByTagName("Policy");
        List<ComponentAssignmentEntry> assignments = new ArrayList<>();

        for (int i = 0; i < policyNodes.getLength(); i++) {
            Node node = policyNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element policy = (Element) node;
            String policyId = attr(policy, "ID");
            String platformId = attr(policy, "PlatformBaseID");
            if (platformId.isBlank()) {
                platformId = findDeviceName(policy);
            }

            for (Element component : listConnectionComponents(policy)) {
                String componentId = attr(component, "Id");
                if (!componentId.equalsIgnoreCase(targetComponentId)) {
                    continue;
                }

                assignments.add(new ComponentAssignmentEntry(
                        platformId,
                        policyId,
                        componentId,
                        boolLabel(attr(component, "Enable")),
                        hasOverrides(component) ? "Yes" : "No"
                ));
            }
        }

        return assignments;
    }

    public List<ComponentAssignmentEntry> getComponentsForPolicy(String policiesPath, String policyIdFilter) throws Exception {
        String targetPolicyId = policyIdFilter == null ? "" : policyIdFilter.trim();
        if (targetPolicyId.isBlank()) {
            return List.of();
        }

        Document doc = loadDocument(policiesPath);
        NodeList policyNodes = doc.getElementsByTagName("Policy");
        List<ComponentAssignmentEntry> rows = new ArrayList<>();

        for (int i = 0; i < policyNodes.getLength(); i++) {
            Node node = policyNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element policy = (Element) node;
            String policyId = attr(policy, "ID");
            if (!policyId.equalsIgnoreCase(targetPolicyId)) {
                continue;
            }

            String platformId = attr(policy, "PlatformBaseID");
            if (platformId.isBlank()) {
                platformId = findDeviceName(policy);
            }

            for (Element component : listConnectionComponents(policy)) {
                rows.add(new ComponentAssignmentEntry(
                        platformId,
                        policyId,
                        attr(component, "Id"),
                        boolLabel(attr(component, "Enable")),
                        hasOverrides(component) ? "Yes" : "No"
                ));
            }
        }

        return rows;
    }

    public List<UsagePolicyEntry> getPoliciesForUsage(String policiesPath, String usageIdFilter) throws Exception {
        String targetUsageId = usageIdFilter == null ? "" : usageIdFilter.trim();
        if (targetUsageId.isBlank()) {
            return List.of();
        }

        Document doc = loadDocument(policiesPath);
        NodeList policyNodes = doc.getElementsByTagName("Policy");
        List<UsagePolicyEntry> rows = new ArrayList<>();

        for (int i = 0; i < policyNodes.getLength(); i++) {
            Node node = policyNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element policy = (Element) node;
            String policyId = attr(policy, "ID");
            String platformId = attr(policy, "PlatformBaseID");
            if (platformId.isBlank()) {
                platformId = findDeviceName(policy);
            }

            Element usagesElement = firstDirectChild(policy, "Usages");
            if (usagesElement == null) {
                continue;
            }

            boolean matched = false;
            for (Element usage : directChildElements(usagesElement, "Usage")) {
                if (attr(usage, "Name").equalsIgnoreCase(targetUsageId)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                continue;
            }

            List<String> componentIds = new ArrayList<>();
            boolean overrides = false;
            for (Element component : listConnectionComponents(policy)) {
                String componentId = attr(component, "Id");
                if (!componentId.isBlank()) {
                    componentIds.add(componentId);
                }
                if (hasOverrides(component)) {
                    overrides = true;
                }
            }

            rows.add(new UsagePolicyEntry(
                    policyId,
                    platformId,
                    String.join(", ", componentIds),
                    overrides ? "Yes" : "No"
            ));
        }

        return rows;
    }

    public List<usageEntry> getUsage(String policiesPath) throws Exception {
        Document doc = loadDocument(policiesPath);
        Map<String, UsageContext> usages = new LinkedHashMap<>();

        Element rootUsages = firstDirectChild(doc.getDocumentElement(), "Usages");
        if (rootUsages != null) {
            for (Element usage : directChildElements(rootUsages, "Usage")) {
                String usageId = attr(usage, "ID");
                if (usageId.isBlank()) {
                    continue;
                }

                usages.put(usageId, new UsageContext(
                        usageId,
                        attr(usage, "PlatformBaseID"),
                        attr(usage, "PlatformBaseProtocol"),
                        attr(usage, "PlatformBaseType"),
                        parseElementTree(usage)
                ));
            }
        }

        NodeList policyNodes = doc.getElementsByTagName("Policy");
        for (int i = 0; i < policyNodes.getLength(); i++) {
            Node node = policyNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element policy = (Element) node;
            String policyId = attr(policy, "ID");
            String platformId = attr(policy, "PlatformBaseID");
            if (platformId.isBlank()) {
                platformId = findDeviceName(policy);
            }

            Element usagesElement = firstDirectChild(policy, "Usages");
            if (usagesElement == null) {
                continue;
            }

            for (Element usage : directChildElements(usagesElement, "Usage")) {
                String usageName = attr(usage, "Name");
                if (usageName.isBlank()) {
                    continue;
                }

                UsageContext context = usages.computeIfAbsent(usageName, key -> new UsageContext(usageName, "", "", "", List.of()));
                context.policies.add(policyId);
            }
        }

        List<usageEntry> entries = new ArrayList<>();
        for (UsageContext context : usages.values()) {
            entries.add(new usageEntry(
                    context.usageId,
                    context.platformBaseId,
                    context.platformBaseProtocol,
                    context.platformBaseType,
                    context.policies.size(),
                    context.children
            ));
        }

        return entries;
    }

    public List<TargetEntry> getTargets(String pvConfigurationPath) throws Exception {
        PVConfigurationParser pvParser = new PVConfigurationParser();
        List<PVConfigurationParser.ConnectionComponentEntry> components = pvParser.GetConnectionComponents(pvConfigurationPath);
        List<TargetEntry> targets = new ArrayList<>();

        for (PVConfigurationParser.ConnectionComponentEntry component : components) {
            String targetAddress = extractTargetFromComponentDetails(component.details());
            if (!targetAddress.isBlank()) {
                targets.add(new TargetEntry(targetAddress, targetAddress, "", component.id(), ""));
            }
        }

        return targets;
    }

    public List<AlteredAddressEntry> getAggregatedTargetsByAlteredAddress(String pvConfigurationPath) throws Exception {
        List<TargetEntry> targets = getTargets(pvConfigurationPath);
        Map<String, Integer> addressCounts = new LinkedHashMap<>();

        for (TargetEntry target : targets) {
            String normalizedAddress = normalizeAddress(target.effectiveAddress());
            if (!normalizedAddress.isBlank()) {
                addressCounts.put(normalizedAddress, addressCounts.getOrDefault(normalizedAddress, 0) + 1);
            }
        }

        List<AlteredAddressEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : addressCounts.entrySet()) {
            entries.add(new AlteredAddressEntry(entry.getKey(), entry.getValue()));
        }

        return entries;
    }

    public List<TargetDetailEntry> getTargetDetailsForAddress(String pvConfigurationPath, String address) throws Exception {
        String normalizedSelectedAddress = normalizeAddress(address);
        if (normalizedSelectedAddress.isBlank()) {
            return List.of();
        }

        PVConfigurationParser pvParser = new PVConfigurationParser();
        List<PVConfigurationParser.ConnectionComponentEntry> components = pvParser.GetConnectionComponents(pvConfigurationPath);
        List<TargetDetailEntry> details = new ArrayList<>();
        Set<String> uniqueRows = new LinkedHashSet<>();

        for (PVConfigurationParser.ConnectionComponentEntry component : components) {
            String normalizedAddress = normalizeAddress(extractTargetFromComponentDetails(component.details()));
            if (!normalizedSelectedAddress.equals(normalizedAddress)) {
                continue;
            }

            String platformOrPolicyName = component.name() == null || component.name().isBlank()
                    ? component.id()
                    : component.name();
            String customComponentId = component.id() == null ? "" : component.id();

            String uniqueKey = platformOrPolicyName + "\u0000" + customComponentId;
            if (uniqueRows.add(uniqueKey)) {
                details.add(new TargetDetailEntry(platformOrPolicyName, customComponentId));
            }
        }

        return details;
    }

    private static String normalizeAddress(String address) {
        if (address == null) {
            return "";
        }
        return address.trim().toLowerCase(Locale.ROOT);
    }

    private String extractTargetFromComponentDetails(PVConfigurationParser.XmlNode details) {
        if (details == null || details.children() == null) {
            return "";
        }
        String alternateAddress = searchForAlternateFullAddress(details);
        if (!alternateAddress.isBlank()) {
            return alternateAddress;
        }
        return searchForTargetAddress(details);
    }

    private String searchForAlternateFullAddress(PVConfigurationParser.XmlNode node) {
        if (node == null || node.children() == null) {
            return "";
        }
        for (PVConfigurationParser.XmlNode child : node.children()) {
            if (child == null) {
                continue;
            }
            if ("Parameter".equalsIgnoreCase(child.name())) {
                String name = child.attributes() == null ? "" : child.attributes().getOrDefault("Name", "");
                if ("alternate full address:s".equalsIgnoreCase(name)) {
                    String value = child.attributes() == null ? "" : child.attributes().getOrDefault("Value", "");
                    if (!value.isBlank()) {
                        return value;
                    }
                }
            }
            String nested = searchForAlternateFullAddress(child);
            if (!nested.isBlank()) {
                return nested;
            }
        }
        return "";
    }

    private String searchForTargetAddress(PVConfigurationParser.XmlNode node) {
        if (node == null || node.children() == null) {
            return "";
        }

        for (PVConfigurationParser.XmlNode child : node.children()) {
            if (child == null) continue;

            if ("PSMRemoteMachine".equalsIgnoreCase(child.name()) ||
                "RemoteMachine".equalsIgnoreCase(child.name()) ||
                "TargetAddress".equalsIgnoreCase(child.name()) ||
                "Address".equalsIgnoreCase(child.name())) {
                String value = child.attributes() == null ? "" : child.attributes().getOrDefault("Value", "");
                if (!value.isBlank()) {
                    return value;
                }
            }

            String found = searchForTargetAddress(child);
            if (!found.isBlank()) {
                return found;
            }
        }
        return "";
    }

    private Document loadDocument(String xmlPath) throws Exception {
        return loadSecureDocument(xmlPath);
    }

    private static String findDeviceName(Element policyElement) {
        Node parent = policyElement.getParentNode();
        while (parent != null) {
            if (parent.getNodeType() == Node.ELEMENT_NODE) {
                Element parentElement = (Element) parent;
                if ("Device".equalsIgnoreCase(parentElement.getTagName())) {
                    String name = attr(parentElement, "Name");
                    return name.isBlank() ? "Unknown" : name;
                }
            }
            parent = parent.getParentNode();
        }
        return "Unknown";
    }

    private static List<Element> listConnectionComponents(Element policy) {
        Element connectionComponents = firstDirectChild(policy, "ConnectionComponents");
        if (connectionComponents == null) {
            return List.of();
        }
        return directChildElements(connectionComponents, "ConnectionComponent");
    }

    private static Element firstDirectChild(Element parent, String childName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && childName.equalsIgnoreCase(((Element) node).getTagName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private static List<Element> directChildElements(Element parent, String childName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && childName.equalsIgnoreCase(((Element) node).getTagName())) {
                result.add((Element) node);
            }
        }
        return result;
    }


    private static boolean hasOverrides(Element connectionComponent) {
        if (firstDirectChild(connectionComponent, "OverrideUserParameters") != null) {
            return true;
        }
        return firstDirectChild(connectionComponent, "OverrideComponentParameters") != null;
    }

    private static String boolLabel(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("yes".equals(normalized) || "true".equals(normalized) || "1".equals(normalized) || "enabled".equals(normalized)) {
            return "Yes";
        }
        if ("no".equals(normalized) || "false".equals(normalized) || "0".equals(normalized) || "disabled".equals(normalized)) {
            return "No";
        }
        return value;
    }

    private static class UsageContext {
        private final String usageId;
        private final String platformBaseId;
        private final String platformBaseProtocol;
        private final String platformBaseType;
        private final List<XmlNode> children;
        private final Set<String> policies = new LinkedHashSet<>();

        private UsageContext(String usageId, String platformBaseId, String platformBaseProtocol, String platformBaseType, XmlNode details) {
            this.usageId = usageId;
            this.platformBaseId = platformBaseId;
            this.platformBaseProtocol = platformBaseProtocol;
            this.platformBaseType = platformBaseType;
            this.children = details == null ? List.of() : List.of(details);
        }

        private UsageContext(String usageId, String platformBaseId, String platformBaseProtocol, String platformBaseType, List<XmlNode> children) {
            this.usageId = usageId;
            this.platformBaseId = platformBaseId;
            this.platformBaseProtocol = platformBaseProtocol;
            this.platformBaseType = platformBaseType;
            this.children = children == null ? List.of() : children;
        }
    }
}

