import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

public final class Compare {

    private Compare() {
    }

    public enum Kind {
        CONNECTION_COMPONENT("Connection Component"),
        USAGE("Usage"),
        POLICY("Policy (Platform)");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Status {
        EQUAL,
        DIFFERENT,
        ONLY_A,
        ONLY_B
    }

    public record Item(String id, String label) {
        @Override
        public String toString() {
            return label == null || label.isBlank() ? id : label;
        }
    }

    public record Row(String property, String valueA, String valueB, Status status) {
    }

    public record Result(String title, String subtitleA, String subtitleB, List<Row> rows, int differences) {
    }

    // ---------------------------------------------------------------------------------------- diff

    public static Map<String, String> flatten(PVConfigurationParser.XmlNode node) {
        Map<String, String> out = new LinkedHashMap<>();
        if (node != null) {
            flattenNode(node, node.name() == null ? "" : node.name(), out);
        }
        return out;
    }

    private static void flattenNode(PVConfigurationParser.XmlNode node, String path, Map<String, String> out) {
        if (node.attributes() != null) {
            for (Map.Entry<String, String> attribute : node.attributes().entrySet()) {
                out.put(path + " @" + attribute.getKey(), attribute.getValue() == null ? "" : attribute.getValue());
            }
        }
        List<PVConfigurationParser.XmlNode> children = node.children();
        if (children == null) {
            return;
        }
        Map<String, Integer> seen = new HashMap<>();
        for (PVConfigurationParser.XmlNode child : children) {
            if (child == null) {
                continue;
            }
            String childPath = path + " / " + segmentFor(child);
            int occurrence = seen.merge(childPath, 1, Integer::sum);
            if (occurrence > 1) {
                childPath = childPath + " #" + occurrence;
            }
            flattenNode(child, childPath, out);
        }
    }

    private static String segmentFor(PVConfigurationParser.XmlNode child) {
        String name = child.name() == null ? "?" : child.name();
        Map<String, String> attributes = child.attributes();
        if (attributes != null) {
            for (String keyAttr : List.of("Name", "Id", "ID", "Key")) {
                String value = attributes.get(keyAttr);
                if (value != null && !value.isBlank()) {
                    return name + "[" + value + "]";
                }
            }
        }
        return name;
    }

    public static List<Row> diff(Map<String, String> a, Map<String, String> b) {
        TreeSet<String> keys = new TreeSet<>();
        keys.addAll(a.keySet());
        keys.addAll(b.keySet());

        List<Row> rows = new ArrayList<>();
        for (String key : keys) {
            boolean hasA = a.containsKey(key);
            boolean hasB = b.containsKey(key);
            String valueA = a.getOrDefault(key, "");
            String valueB = b.getOrDefault(key, "");

            Status status;
            if (hasA && hasB) {
                status = Objects.equals(valueA, valueB) ? Status.EQUAL : Status.DIFFERENT;
            } else if (hasA) {
                status = Status.ONLY_A;
            } else {
                status = Status.ONLY_B;
            }
            rows.add(new Row(key, valueA, valueB, status));
        }
        return rows;
    }

    public static int countDifferences(List<Row> rows) {
        int differences = 0;
        for (Row row : rows) {
            if (row.status() != Status.EQUAL) {
                differences++;
            }
        }
        return differences;
    }
}
