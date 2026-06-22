import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ComponentOperations (offline edits)")
class ComponentOperationsTest {

    private final ComponentOperations ops = new ComponentOperations();
    private final PVConfigurationParser pvParser = new PVConfigurationParser();
    private final PoliciesParser policiesParser = new PoliciesParser();

    private static final Function<String, ComponentOperations.EmptyPolicyChoice> ADD_RDP =
            id -> ComponentOperations.EmptyPolicyChoice.add("PSM-RDP", true, false);
    private static final Function<String, ComponentOperations.EmptyPolicyChoice> CANCEL =
            id -> ComponentOperations.EmptyPolicyChoice.cancel();
    private static final Function<String, ComponentOperations.EmptyPolicyChoice> FAIL_IF_CALLED =
            id -> {
                throw new AssertionError("empty-policy resolver should not have been called for " + id);
            };

    private Path source(Path dir, String fixture, String name) throws Exception {
        return TestSupport.copyFixture(dir, fixture, name);
    }

    // ----------------------------------------------------------------------------------- export (positive)

    @Test
    @DisplayName("Export writes <id>/PSM-<id>.zip containing CC-<id>.xml")
    void exportWritesZip(@TempDir Path tmp) throws Exception {
        ComponentOperations.ExportResult result =
                ops.exportConnectionComponent(TestSupport.fixturePath("PVConfiguration.xml"), "PSM-RDP", tmp);

        assertEquals("PSM-RDP", result.componentId());
        Path zip = tmp.resolve("PSM-RDP").resolve("PSM-PSM-RDP.zip");
        assertTrue(Files.exists(zip));
        assertEquals(zip.toAbsolutePath(), result.zipPath().toAbsolutePath());

        byte[] zipBytes = Files.readAllBytes(zip);
        String entry = TestSupport.entryFromZipBytes(zipBytes, "CC-PSM-RDP.xml");
        assertNotNull(entry);
        assertTrue(entry.contains("Id=\"PSM-RDP\""));
        assertTrue(entry.contains("<ConnectionComponent"));
    }

    @Test
    @DisplayName("packageConnectionComponent returns an in-memory zip with the component XML")
    void packageInMemory() throws Exception {
        byte[] zip = ops.packageConnectionComponent(TestSupport.fixturePath("PVConfiguration.xml"), "PSM-SSH");
        String entry = TestSupport.entryFromZipBytes(zip, "CC-PSM-SSH.xml");
        assertNotNull(entry);
        assertTrue(entry.contains("Id=\"PSM-SSH\""));
    }

    // ----------------------------------------------------------------------------------- export (negative)

    @Test
    @DisplayName("Export of unknown component fails")
    void exportUnknownComponent(@TempDir Path tmp) {
        assertThrows(IllegalArgumentException.class,
                () -> ops.exportConnectionComponent(TestSupport.fixturePath("PVConfiguration.xml"), "NOPE", tmp));
    }

    @Test
    @DisplayName("Export with blank id fails")
    void exportBlankId(@TempDir Path tmp) {
        assertThrows(IllegalArgumentException.class,
                () -> ops.exportConnectionComponent(TestSupport.fixturePath("PVConfiguration.xml"), "  ", tmp));
    }

    // ----------------------------------------------------------------------------------- import (positive)

    @Test
    @DisplayName("Import inserts a new component definition into PVConfiguration")
    void importNewComponent(@TempDir Path tmp) throws Exception {
        Path pv = source(tmp.resolve("src"), "PVConfiguration.xml", "PVConfiguration.xml");
        Path zip = TestSupport.writeZip(tmp.resolve("CC-PSM-NEW.zip"), "CC-PSM-NEW.xml",
                TestSupport.readFixture("import/CC-PSM-NEW.xml"));
        Path out = tmp.resolve("out");

        ComponentOperations.ImportResult result =
                ops.importConnectionComponents(pv.toString(), List.of(zip), out, "LPC", pv.getParent().toString());

        assertTrue(result.imported());
        assertEquals(List.of("PSM-NEW"), result.importedIds());
        assertTrue(result.skipped().isEmpty());

        var imported = pvParser.GetConnectionComponents(result.outputPvConfiguration().toString());
        assertEquals(6, imported.size());
        assertTrue(imported.stream().anyMatch(c -> c.id().equals("PSM-NEW")));
    }

    // ----------------------------------------------------------------------------------- import (negative)

    @Test
    @DisplayName("Import skips a component whose id already exists")
    void importDuplicateSkipped(@TempDir Path tmp) throws Exception {
        Path pv = source(tmp.resolve("src"), "PVConfiguration.xml", "PVConfiguration.xml");
        String duplicate = "<ConnectionComponent Id=\"PSM-RDP\" DisplayName=\"dup\" />";
        Path zip = TestSupport.writeZip(tmp.resolve("dup.zip"), "CC-PSM-RDP.xml", duplicate);
        Path out = tmp.resolve("out");

        ComponentOperations.ImportResult result =
                ops.importConnectionComponents(pv.toString(), List.of(zip), out, "LPC", null);

        assertFalse(result.imported());
        assertTrue(result.importedIds().isEmpty());
        assertEquals(1, result.skipped().size());
        assertTrue(result.skipped().get(0).contains("already exists"));
    }

    @Test
    @DisplayName("Import with no files fails")
    void importNoFiles(@TempDir Path tmp) {
        assertThrows(IllegalArgumentException.class,
                () -> ops.importConnectionComponents(TestSupport.fixturePath("PVConfiguration.xml"),
                        List.of(), tmp, "LPC", null));
    }

    @Test
    @DisplayName("Import into a file without a ConnectionComponents container fails")
    void importNoContainer(@TempDir Path tmp) throws Exception {
        Path zip = TestSupport.writeZip(tmp.resolve("CC-PSM-NEW.zip"), "CC-PSM-NEW.xml",
                TestSupport.readFixture("import/CC-PSM-NEW.xml"));
        assertThrows(IllegalStateException.class,
                () -> ops.importConnectionComponents(TestSupport.fixturePath("no-connection-components.xml"),
                        List.of(zip), tmp, "LPC", null));
    }

    // ------------------------------------------------------------------------------------ order (positive)

    @Test
    @DisplayName("Order reorders PVConfiguration definitions and a policy's assigned components")
    void orderReorders(@TempDir Path tmp) throws Exception {
        Path pv = source(tmp.resolve("src"), "PVConfiguration.xml", "PVConfiguration.xml");
        Path policies = source(tmp.resolve("src"), "Policies.xml", "Policies.xml");
        Path out = tmp.resolve("out");

        ComponentOperations.OrderResult result = ops.applyComponentOrder(
                pv.toString(), policies.toString(),
                List.of("PSM-VSPHERE", "PSM-SSH", "PSM-RDP", "WebConnection", "RDPWinApplet"),
                Map.of("MultiPolicy", List.of("WebConnection", "PSM-RDP", "PSM-SSH")),
                out, "LPC", pv.getParent().toString());

        assertEquals(5, result.pvReordered());
        assertEquals(1, result.policiesReordered());

        var reordered = pvParser.GetConnectionComponents(result.outputPvConfiguration().toString());
        assertEquals("PSM-VSPHERE", reordered.get(0).id());

        var multi = policiesParser.getComponentsForPolicy(result.outputPolicies().toString(), "MultiPolicy");
        assertEquals(List.of("WebConnection", "PSM-RDP", "PSM-SSH"),
                multi.stream().map(PoliciesParser.ComponentAssignmentEntry::componentId).toList());
    }

    // ----------------------------------------------------------------------------------- remove (positive)

    @Test
    @DisplayName("Remove deletes policy references and PVConfiguration definitions")
    void removeComponent(@TempDir Path tmp) throws Exception {
        Path pv = source(tmp.resolve("src"), "PVConfiguration.xml", "PVConfiguration.xml");
        Path policies = source(tmp.resolve("src"), "Policies.xml", "Policies.xml");
        Path out = tmp.resolve("out");

        ComponentOperations.RemovalResult result = ops.removeConnectionComponents(
                policies.toString(), pv.toString(), List.of("WebConnection"),
                out, "LPC", pv.getParent().toString(), FAIL_IF_CALLED);

        assertFalse(result.cancelled());
        assertEquals(1, result.totalRemovedAssignments());
        assertEquals(1, result.removedDefinitions());

        var pvAfter = pvParser.GetConnectionComponents(result.outputPvConfiguration().toString());
        assertFalse(pvAfter.stream().anyMatch(c -> c.id().equals("WebConnection")));

        var multi = policiesParser.getComponentsForPolicy(result.outputPolicies().toString(), "MultiPolicy");
        assertEquals(2, multi.size());
    }

    @Test
    @DisplayName("Remove never mutates the source files (non-destructive)")
    void removeIsNonDestructive(@TempDir Path tmp) throws Exception {
        Path pv = source(tmp.resolve("src"), "PVConfiguration.xml", "PVConfiguration.xml");
        Path policies = source(tmp.resolve("src"), "Policies.xml", "Policies.xml");
        byte[] pvBefore = Files.readAllBytes(pv);
        byte[] policiesBefore = Files.readAllBytes(policies);

        ops.removeConnectionComponents(policies.toString(), pv.toString(), List.of("WebConnection"),
                tmp.resolve("out"), "LPC", null, FAIL_IF_CALLED);

        assertArrayEquals(pvBefore, Files.readAllBytes(pv));
        assertArrayEquals(policiesBefore, Files.readAllBytes(policies));
    }

    // -------------------------------------------------------------------------- empty-policy edge (positive)

    @Test
    @DisplayName("Removal that empties a policy inserts the chosen replacement component")
    void emptyPolicyReplacement(@TempDir Path tmp) throws Exception {
        Path pv = source(tmp.resolve("src"), "PVConfiguration.xml", "PVConfiguration.xml");
        Path policies = source(tmp.resolve("src"), "Policies.xml", "Policies.xml");
        Path out = tmp.resolve("out");

        ComponentOperations.RemovalResult result = ops.removeConnectionComponents(
                policies.toString(), pv.toString(), List.of("PSM-SSH"),
                out, "LPC", null, ADD_RDP);

        assertFalse(result.cancelled());
        assertEquals(2, result.totalRemovedAssignments());
        assertEquals(1, result.emptyPoliciesFixed().size());
        assertTrue(result.emptyPoliciesFixed().get(0).startsWith("LonePolicy -> PSM-RDP"));

        var lone = policiesParser.getComponentsForPolicy(result.outputPolicies().toString(), "LonePolicy");
        assertEquals(1, lone.size());
        assertEquals("PSM-RDP", lone.get(0).componentId());
        assertEquals("Yes", lone.get(0).componentEnabled());
    }

    // -------------------------------------------------------------------------- empty-policy edge (negative)

    @Test
    @DisplayName("Cancelling the empty-policy prompt aborts the whole operation")
    void emptyPolicyCancelAborts(@TempDir Path tmp) throws Exception {
        Path pv = source(tmp.resolve("src"), "PVConfiguration.xml", "PVConfiguration.xml");
        Path policies = source(tmp.resolve("src"), "Policies.xml", "Policies.xml");

        ComponentOperations.RemovalResult result = ops.removeConnectionComponents(
                policies.toString(), pv.toString(), List.of("PSM-SSH"),
                tmp.resolve("out"), "LPC", null, CANCEL);

        assertTrue(result.cancelled());
        assertNull(result.outputPolicies());
        assertNull(result.outputPvConfiguration());
    }

    @Test
    @DisplayName("Remove with no components selected fails")
    void removeNoSelection(@TempDir Path tmp) throws Exception {
        Path pv = source(tmp.resolve("src"), "PVConfiguration.xml", "PVConfiguration.xml");
        Path policies = source(tmp.resolve("src"), "Policies.xml", "Policies.xml");
        assertThrows(IllegalArgumentException.class,
                () -> ops.removeConnectionComponents(policies.toString(), pv.toString(), List.of(),
                        tmp.resolve("out"), "LPC", null, ADD_RDP));
    }

    // ----------------------------------------------------------------------------------- unlink (positive)

    @Test
    @DisplayName("Unlink removes policy references but leaves PVConfiguration untouched")
    void unlinkOnlyPolicies(@TempDir Path tmp) throws Exception {
        Path policies = source(tmp.resolve("src"), "Policies.xml", "Policies.xml");
        Path out = tmp.resolve("out");

        ComponentOperations.RemovalResult result = ops.unlinkConnectionComponents(
                policies.toString(), List.of("WebConnection"), out, "LPC", null, FAIL_IF_CALLED);

        assertFalse(result.cancelled());
        assertEquals(1, result.totalRemovedAssignments());
        assertEquals(0, result.removedDefinitions());
        assertNotNull(result.outputPolicies());
        assertNull(result.outputPvConfiguration());

        var multi = policiesParser.getComponentsForPolicy(result.outputPolicies().toString(), "MultiPolicy");
        assertFalse(multi.stream().anyMatch(c -> c.componentId().equals("WebConnection")));
    }

    // ------------------------------------------------------------------------------------ sanitize helper

    @Test
    @DisplayName("sanitizeFileName replaces unsafe characters and defaults blanks")
    void sanitizeFileName() throws Exception {
        Method m = ComponentOperations.class.getDeclaredMethod("sanitizeFileName", String.class);
        m.setAccessible(true);
        assertEquals("PSM-RDP", m.invoke(null, "PSM-RDP"));
        assertEquals("a_b_c", m.invoke(null, "a/b\\c"));
        assertEquals("unnamed", m.invoke(null, "   "));
        assertEquals("unnamed", m.invoke(null, (Object) null));
    }
}

