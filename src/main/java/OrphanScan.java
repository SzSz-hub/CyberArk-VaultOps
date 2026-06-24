import java.util.List;

public final class OrphanScan {

    public record Result(
            String sourceLabel,
            int definedComponents,
            int scannedAssignments,
            List<PoliciesParser.ComponentAssignmentEntry> orphans) {
    }

    private OrphanScan() {
    }
}

