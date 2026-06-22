import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PoliciesParser")
class PoliciesParserTest {

    private final PoliciesParser parser = new PoliciesParser();

    private static final String POLICIES = "Policies.xml";
    private static final String PVCONFIG = "PVConfiguration.xml";

    private Map<String, PoliciesParser.PolicyEntry> policiesById() throws Exception {
        return parser.getPolicies(TestSupport.fixturePath(POLICIES))
                .stream().collect(Collectors.toMap(PoliciesParser.PolicyEntry::policyId, p -> p));
    }

    // ------------------------------------------------------------------------------------------- positive

    @Test
    @DisplayName("Parses every policy")
    void parsesAllPolicies() throws Exception {
        assertEquals(3, parser.getPolicies(TestSupport.fixturePath(POLICIES)).size());
    }

    @Test
    @DisplayName("Single-component policy reports platform, count and assignment")
    void singleComponentPolicy() throws Exception {
        PoliciesParser.PolicyEntry single = policiesById().get("SinglePolicy");
        assertNotNull(single);
        assertEquals("WindowsServerLocal", single.platformId());
        assertEquals("Yes", single.platformEnabled());
        assertEquals(1, single.componentCount());
        assertEquals("PSM-RDP", single.assignedComponents());
        assertEquals("No", single.hasOverrides());
    }

    @Test
    @DisplayName("Policy without PlatformBaseID falls back to the device name")
    void deviceNameFallback() throws Exception {
        PoliciesParser.PolicyEntry multi = policiesById().get("MultiPolicy");
        assertNotNull(multi);
        assertEquals("Application", multi.platformId());
        assertEquals(3, multi.componentCount());
        assertEquals("PSM-SSH, PSM-RDP, WebConnection", multi.assignedComponents());
    }

    @Test
    @DisplayName("Override children are detected as overrides")
    void detectsOverrides() throws Exception {
        assertEquals("Yes", policiesById().get("MultiPolicy").hasOverrides());
    }

    @Test
    @DisplayName("Assignments for a component span every policy that references it")
    void assignmentsForComponent() throws Exception {
        List<PoliciesParser.ComponentAssignmentEntry> assignments =
                parser.getAssignmentsForConnectionComponent(TestSupport.fixturePath(POLICIES), "PSM-RDP");
        assertEquals(2, assignments.size());
        assertTrue(assignments.stream().anyMatch(a -> a.policyId().equals("SinglePolicy") && a.componentEnabled().equals("Unknown")));
        assertTrue(assignments.stream().anyMatch(a -> a.policyId().equals("MultiPolicy") && a.componentEnabled().equals("Yes")));
    }

    @Test
    @DisplayName("Components for a policy enumerate every assignment in order")
    void componentsForPolicy() throws Exception {
        List<PoliciesParser.ComponentAssignmentEntry> rows =
                parser.getComponentsForPolicy(TestSupport.fixturePath(POLICIES), "MultiPolicy");
        assertEquals(3, rows.size());
        assertEquals("PSM-SSH", rows.get(0).componentId());
        assertEquals("No", rows.get(0).componentEnabled());
    }

    @Test
    @DisplayName("Root usage metadata is parsed and policy references counted")
    void parsesUsages() throws Exception {
        List<PoliciesParser.usageEntry> usages = parser.getUsage(TestSupport.fixturePath(POLICIES));
        PoliciesParser.usageEntry iis = usages.stream()
                .filter(u -> u.usageId().equals("IISAppPool")).findFirst().orElseThrow();
        assertEquals("IISAppPool", iis.platformBaseId());
        assertEquals(1, iis.policyCount());
    }

    @Test
    @DisplayName("Policies for a usage are resolved through the usage reference")
    void policiesForUsage() throws Exception {
        List<PoliciesParser.UsagePolicyEntry> rows =
                parser.getPoliciesForUsage(TestSupport.fixturePath(POLICIES), "IISAppPool");
        assertEquals(1, rows.size());
        assertEquals("SinglePolicy", rows.get(0).policyId());
    }

    @Test
    @DisplayName("Targets are extracted from the alternate full address parameter")
    void targetsFromAlternateAddress() throws Exception {
        List<PoliciesParser.TargetEntry> targets = parser.getTargets(TestSupport.fixturePath(PVCONFIG));
        assertEquals(1, targets.size());
        assertEquals("psm.example.com", targets.get(0).effectiveAddress());
        assertEquals("PSM-RDP", targets.get(0).platformId());
    }

    @Test
    @DisplayName("Targets aggregate by altered (normalized) address")
    void aggregatedTargets() throws Exception {
        List<PoliciesParser.AlteredAddressEntry> entries =
                parser.getAggregatedTargetsByAlteredAddress(TestSupport.fixturePath(PVCONFIG));
        assertEquals(1, entries.size());
        assertEquals("psm.example.com", entries.get(0).address());
        assertEquals(1, entries.get(0).count());
    }

    // ------------------------------------------------------------------------------------------- negative

    @Test
    @DisplayName("Blank component id yields no assignments")
    void blankComponentIdNoAssignments() throws Exception {
        assertTrue(parser.getAssignmentsForConnectionComponent(TestSupport.fixturePath(POLICIES), "  ").isEmpty());
    }

    @Test
    @DisplayName("Unknown policy id yields no components")
    void unknownPolicyNoComponents() throws Exception {
        assertTrue(parser.getComponentsForPolicy(TestSupport.fixturePath(POLICIES), "NoSuchPolicy").isEmpty());
    }

    @Test
    @DisplayName("Malformed policies file throws")
    void malformedPoliciesThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> parser.getPolicies(TestSupport.fixturePath("malformed.xml")));
    }
}

