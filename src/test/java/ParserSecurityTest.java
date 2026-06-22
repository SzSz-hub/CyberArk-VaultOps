import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Secure XML loading (XXE / malformed hardening)")
class ParserSecurityTest {

    // ----------------------------------------------------------------------------- positive (valid parse)

    @Test
    @DisplayName("Parser loads a valid PVConfiguration document")
    void parserLoadsValidDocument() throws Exception {
        var components = new PVConfigurationParser().GetConnectionComponents(TestSupport.fixturePath("PVConfiguration.xml"));
        assertNotNull(components);
        assertTrue(components.size() >= 1);
    }

    // ------------------------------------------------------------------------------------- negative (XXE)

    @Test
    @DisplayName("PVConfigurationParser rejects a document with a DOCTYPE/XXE entity")
    void parserRejectsXxe() {
        assertThrows(Exception.class,
                () -> new PVConfigurationParser().GetConnectionComponents(TestSupport.fixturePath("xxe.xml")));
    }

    @Test
    @DisplayName("PoliciesParser rejects a document with a DOCTYPE/XXE entity")
    void policiesParserRejectsXxe() {
        assertThrows(Exception.class,
                () -> new PoliciesParser().getPolicies(TestSupport.fixturePath("xxe.xml")));
    }

    @Test
    @DisplayName("ComponentOperations factory also rejects DOCTYPE/XXE entities")
    void componentOperationsRejectsXxe() {
        assertThrows(Exception.class,
                () -> new ComponentOperations().exportConnectionComponent(
                        TestSupport.fixturePath("xxe.xml"), "PSM-XXE", Path.of(System.getProperty("java.io.tmpdir"))));
    }

    // ------------------------------------------------------------------------------- negative (malformed)

    @Test
    @DisplayName("Malformed XML fails to parse")
    void malformedXmlFails() {
        assertThrows(Exception.class,
                () -> new PVConfigurationParser().GetConnectionComponents(TestSupport.fixturePath("malformed.xml")));
    }

    @Test
    @DisplayName("Empty XML fails to parse")
    void emptyXmlFails() {
        assertThrows(Exception.class,
                () -> new PVConfigurationParser().GetConnectionComponents(TestSupport.fixturePath("empty.xml")));
    }

    @Test
    @DisplayName("Missing file fails to parse")
    void missingFileFails() {
        assertThrows(Exception.class,
                () -> new PVConfigurationParser().GetConnectionComponents("does-not-exist.xml"));
    }
}

