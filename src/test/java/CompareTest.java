import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Compare (flatten / diff)")
class CompareTest {

    private static Parser.XmlNode node(String name, Map<String, String> attrs, Parser.XmlNode... children) {
        return new Parser.XmlNode(name, attrs, List.of(children));
    }

    private static Map<String, String> attrs(String... kv) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }

    // --------------------------------------------------------------------------------- flatten (positive)

    @Test
    @DisplayName("flatten renders attributes as @attr under the node path")
    void flattenAttributes() {
        Map<String, String> flat = Compare.flatten(node("Root", attrs("Id", "PSM-RDP", "Enabled", "Yes")));
        assertEquals("PSM-RDP", flat.get("Root @Id"));
        assertEquals("Yes", flat.get("Root @Enabled"));
    }

    @Test
    @DisplayName("flatten disambiguates repeated children by key attribute")
    void flattenRepeatedChildrenByKey() {
        Parser.XmlNode root = node("Caps", attrs(),
                node("Capability", attrs("Id", "A")),
                node("Capability", attrs("Id", "B")));
        Map<String, String> flat = Compare.flatten(root);
        assertTrue(flat.containsKey("Caps / Capability[A] @Id"));
        assertTrue(flat.containsKey("Caps / Capability[B] @Id"));
    }

    @Test
    @DisplayName("flatten disambiguates indistinguishable repeated children by occurrence index")
    void flattenRepeatedChildrenByIndex() {
        Parser.XmlNode root = node("List", attrs(),
                node("Item", attrs("Value", "1")),
                node("Item", attrs("Value", "2")));
        Map<String, String> flat = Compare.flatten(root);
        assertEquals("1", flat.get("List / Item @Value"));
        assertEquals("2", flat.get("List / Item #2 @Value"));
    }

    // --------------------------------------------------------------------------------- flatten (negative)

    @Test
    @DisplayName("flatten of null returns an empty map")
    void flattenNull() {
        assertTrue(Compare.flatten(null).isEmpty());
    }

    // ------------------------------------------------------------------------------------ diff (positive)

    @Test
    @DisplayName("diff classifies EQUAL / DIFFERENT / ONLY_A / ONLY_B")
    void diffStatuses() {
        Map<String, String> a = attrs("same", "x", "changed", "1", "onlyA", "a");
        Map<String, String> b = attrs("same", "x", "changed", "2", "onlyB", "b");
        Map<String, Compare.Status> byKey = new LinkedHashMap<>();
        for (Compare.Row row : Compare.diff(a, b)) {
            byKey.put(row.property(), row.status());
        }
        assertEquals(Compare.Status.EQUAL, byKey.get("same"));
        assertEquals(Compare.Status.DIFFERENT, byKey.get("changed"));
        assertEquals(Compare.Status.ONLY_A, byKey.get("onlyA"));
        assertEquals(Compare.Status.ONLY_B, byKey.get("onlyB"));
    }

    @Test
    @DisplayName("countDifferences counts every non-EQUAL row")
    void countDifferences() {
        List<Compare.Row> rows = Compare.diff(
                attrs("same", "x", "changed", "1", "onlyA", "a"),
                attrs("same", "x", "changed", "2", "onlyB", "b"));
        assertEquals(3, Compare.countDifferences(rows));
    }

    // ------------------------------------------------------------------------------------ diff (negative)

    @Test
    @DisplayName("diff of two empty maps produces no rows")
    void diffEmpty() {
        assertTrue(Compare.diff(Map.of(), Map.of()).isEmpty());
    }

    @Test
    @DisplayName("Item.toString falls back to id when label is blank")
    void itemToStringFallback() {
        assertEquals("PSM-RDP", new Compare.Item("PSM-RDP", "").toString());
        assertEquals("RDP", new Compare.Item("PSM-RDP", "RDP").toString());
    }
}

