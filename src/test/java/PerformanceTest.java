import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@DisplayName("Performance (large configs / diffs stay responsive)")
class PerformanceTest {

    private static final int COMPONENTS = 2_000;
    private static final int FLATTEN_NODES = 5_000;

    private Path writeLargePvConfiguration(Path dir) throws Exception {
        StringBuilder sb = new StringBuilder(COMPONENTS * 256);
        sb.append("<PasswordVaultConfiguration>\n  <ConnectionComponents>\n");
        for (int i = 0; i < COMPONENTS; i++) {
            sb.append("    <ConnectionComponent DisplayName=\"Comp ").append(i)
                    .append("\" Id=\"PSM-COMP-").append(i)
                    .append("\" Type=\"CyberArk.PasswordVault.TransparentConnection.PSM.PSMConnectionComponent, CyberArk.PasswordVault.TransparentConnection.PSM\">\n")
                    .append("      <TargetSettings ClientApp=\"client").append(i)
                    .append(".exe\" ClientDispatcher=\"PSMClient.exe\" Protocol=\"RDP\" />\n")
                    .append("    </ConnectionComponent>\n");
        }
        sb.append("  </ConnectionComponents>\n</PasswordVaultConfiguration>\n");

        Path file = dir.resolve("PVConfiguration.xml");
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        return file;
    }

    @Test
    @DisplayName("Parsing a 2,000-component PVConfiguration completes quickly")
    void parseLargeConfiguration(@TempDir Path tmp) throws Exception {
        Path file = writeLargePvConfiguration(tmp);
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            var components = new PVConfigurationParser().GetConnectionComponents(file.toString());
            assertEquals(COMPONENTS, components.size());
        });
    }

    @Test
    @DisplayName("Exporting one component from a large configuration stays fast")
    void exportFromLargeConfiguration(@TempDir Path tmp) throws Exception {
        Path file = writeLargePvConfiguration(tmp);
        Path out = tmp.resolve("out");
        assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> new ComponentOperations().exportConnectionComponent(file.toString(), "PSM-COMP-1999", out));
    }

    @Test
    @DisplayName("Flatten + diff of a wide tree stays responsive")
    void flattenAndDiffLargeTree() {
        Parser.XmlNode wide = buildWideTree();
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            Map<String, String> a = Compare.flatten(wide);
            Map<String, String> b = Compare.flatten(buildWideTree());
            assertEquals(a.size(), b.size());
            List<Compare.Row> rows = Compare.diff(a, b);
            assertEquals(0, Compare.countDifferences(rows));
        });
    }

    private static Parser.XmlNode buildWideTree() {
        java.util.List<Parser.XmlNode> children = new java.util.ArrayList<>(FLATTEN_NODES);
        for (int i = 0; i < FLATTEN_NODES; i++) {
            Map<String, String> attrs = new LinkedHashMap<>();
            attrs.put("Id", "node-" + i);
            attrs.put("Value", Integer.toString(i));
            children.add(new Parser.XmlNode("Item", attrs, List.of()));
        }
        return new Parser.XmlNode("Root", new LinkedHashMap<>(), children);
    }
}

