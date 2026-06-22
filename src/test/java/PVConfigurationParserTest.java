import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PVConfigurationParser")
class PVConfigurationParserTest {

    private final PVConfigurationParser parser = new PVConfigurationParser();

    private Map<String, PVConfigurationParser.ConnectionComponentEntry> componentsById() throws Exception {
        return parser.GetConnectionComponents(TestSupport.fixturePath("PVConfiguration.xml"))
                .stream().collect(Collectors.toMap(PVConfigurationParser.ConnectionComponentEntry::id, c -> c));
    }

    // ------------------------------------------------------------------------------------------- positive

    @Test
    @DisplayName("Parses all connection component definitions")
    void parsesAllComponents() throws Exception {
        List<PVConfigurationParser.ConnectionComponentEntry> components =
                parser.GetConnectionComponents(TestSupport.fixturePath("PVConfiguration.xml"));
        assertEquals(5, components.size());
    }

    @Test
    @DisplayName("PSM component exposes DisplayName and TargetSettings client app/dispatcher")
    void psmComponentFields() throws Exception {
        PVConfigurationParser.ConnectionComponentEntry rdp = componentsById().get("PSM-RDP");
        assertNotNull(rdp);
        assertEquals("RDP", rdp.name());
        assertEquals("mstsc.exe", rdp.ClientApp());
        assertEquals("PSMRdpClient.exe", rdp.ClientDispatcher());
        assertEquals(0, rdp.assignmentCount());
        assertNotNull(rdp.details());
    }

    @Test
    @DisplayName("Generic component without TargetSettings has null client app/dispatcher")
    void genericComponentHasNoTargetSettings() throws Exception {
        PVConfigurationParser.ConnectionComponentEntry generic = componentsById().get("RDPWinApplet");
        assertNotNull(generic);
        assertNull(generic.ClientApp());
        assertNull(generic.ClientDispatcher());
    }

    @Test
    @DisplayName("Missing DisplayName attribute yields empty name, not null")
    void missingDisplayNameIsEmpty() throws Exception {
        PVConfigurationParser.ConnectionComponentEntry web = componentsById().get("WebConnection");
        assertNotNull(web);
        assertEquals("", web.name());
    }

    @Test
    @DisplayName("Parses PSMServer entries with server and TS gateway details")
    void parsesPsmServers() throws Exception {
        List<PVConfigurationParser.PSMServerEntry> servers = parser.getPSMServers(TestSupport.fixturePath("PVConfiguration.xml"));
        assertEquals(2, servers.size());
        PVConfigurationParser.PSMServerEntry first = servers.get(0);
        assertEquals("PSMServer", first.id());
        assertEquals("PSM Server on PSM01", first.name());
        assertEquals("3", first.psmProtocolVersion());
        assertEquals("psm01.example.com", first.serverAddress());
        assertEquals("443", first.serverPort());
        assertEquals("10.0.0.10", first.tsGatewayAddress());
        assertEquals("No", first.tsGatewayEnable());
    }

    @Test
    @DisplayName("Parses PSMPServer entries (server only)")
    void parsesPsmpServers() throws Exception {
        List<PVConfigurationParser.PSMPServerEntry> servers = parser.getPSMPServers(TestSupport.fixturePath("PVConfiguration.xml"));
        assertEquals(2, servers.size());
        assertEquals("PSMPServer_psmp01", servers.get(0).id());
        assertEquals("10.0.0.20", servers.get(0).serverAddress());
        assertEquals("22", servers.get(0).serverPort());
    }

    // ------------------------------------------------------------------------------------------- negative

    @Test
    @DisplayName("File without ConnectionComponents returns empty list (no error)")
    void noComponentsReturnsEmpty() throws Exception {
        List<PVConfigurationParser.ConnectionComponentEntry> components =
                parser.GetConnectionComponents(TestSupport.fixturePath("no-connection-components.xml"));
        assertTrue(components.isEmpty());
    }
}

