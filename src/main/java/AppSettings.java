import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AppSettings {
    public static final int MAX_SOURCES = 10;
    public static final String DEFAULT_THEME = "light";

    public static class SourceProfile {
        private final String id;
        private String displayName;
        private String shortLabel;
        private String folderPath;

        public SourceProfile(String id, String displayName, String shortLabel, String folderPath) {
            this.id = id;
            this.displayName = displayName;
            this.shortLabel = shortLabel;
            this.folderPath = folderPath;
        }

        public SourceProfile(SourceProfile other) {
            this(other.id, other.displayName, other.shortLabel, other.folderPath);
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String shortLabel() {
            return shortLabel;
        }

        public void setShortLabel(String shortLabel) {
            this.shortLabel = shortLabel;
        }

        public String folderPath() {
            return folderPath;
        }

        public void setFolderPath(String folderPath) {
            this.folderPath = folderPath;
        }
    }

    private final List<SourceProfile> sourceProfiles = new ArrayList<>();
    private String activeProfileId;
    private String theme = DEFAULT_THEME;
    private String defaultReplacementComponentId = "";
    private String storageLocation = "";

    public synchronized List<SourceProfile> getSourceProfiles() {
        List<SourceProfile> copy = new ArrayList<>();
        for (SourceProfile profile : sourceProfiles) {
            copy.add(new SourceProfile(profile));
        }
        return copy;
    }

    public synchronized void replaceSourceProfiles(List<SourceProfile> profiles) {
        sourceProfiles.clear();
        for (SourceProfile profile : profiles) {
            if (sourceProfiles.size() >= MAX_SOURCES) {
                break;
            }
            sourceProfiles.add(new SourceProfile(profile));
        }
        ensureValidActiveProfile();
    }

    public synchronized String getActiveProfileId() {
        return activeProfileId;
    }

    public synchronized void setActiveProfileId(String activeProfileId) {
        this.activeProfileId = activeProfileId;
        ensureValidActiveProfile();
    }

    public synchronized SourceProfile getActiveProfile() {
        if (activeProfileId == null || activeProfileId.isBlank()) {
            return null;
        }
        for (SourceProfile profile : sourceProfiles) {
            if (activeProfileId.equals(profile.id())) {
                return new SourceProfile(profile);
            }
        }
        return null;
    }

    public synchronized String getTheme() {
        return theme;
    }

    public synchronized void setTheme(String theme) {
        this.theme = normalizeTheme(theme);
    }

    public synchronized String getDefaultReplacementComponentId() {
        return defaultReplacementComponentId;
    }

    public synchronized void setDefaultReplacementComponentId(String defaultReplacementComponentId) {
        this.defaultReplacementComponentId = safe(defaultReplacementComponentId).trim();
    }

    public synchronized String getStorageLocation() {
        return storageLocation;
    }

    public synchronized void setStorageLocation(String storageLocation) {
        this.storageLocation = safe(storageLocation).trim();
    }

    public synchronized void ensureValidActiveProfile() {
        if (sourceProfiles.isEmpty()) {
            activeProfileId = null;
            return;
        }
        for (SourceProfile profile : sourceProfiles) {
            if (profile.id().equals(activeProfileId)) {
                return;
            }
        }
        activeProfileId = sourceProfiles.get(0).id();
    }

    public static Map<String, String> buildEffectiveShortLabels(List<SourceProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Integer> firstWordCounts = new HashMap<>();
        for (SourceProfile profile : profiles) {
            if (profile.shortLabel() != null && !profile.shortLabel().isBlank()) {
                continue;
            }
            String firstWord = firstWord(profile.displayName());
            if (!firstWord.isBlank()) {
                firstWordCounts.put(firstWord, firstWordCounts.getOrDefault(firstWord, 0) + 1);
            }
        }

        Map<String, String> labels = new LinkedHashMap<>();
        for (SourceProfile profile : profiles) {
            String explicit = safe(profile.shortLabel());
            if (!explicit.isBlank()) {
                labels.put(profile.id(), explicit.toUpperCase(Locale.ROOT));
                continue;
            }

            String firstWord = firstWord(profile.displayName());
            if (!firstWord.isBlank() && firstWordCounts.getOrDefault(firstWord, 0) > 1) {
                labels.put(profile.id(), firstWord.substring(0, Math.min(3, firstWord.length())).toUpperCase(Locale.ROOT));
                continue;
            }

            labels.put(profile.id(), buildInitials(profile.displayName()));
        }

        return labels;
    }

    private static String buildInitials(String displayName) {
        String cleaned = safe(displayName).trim();
        if (cleaned.isBlank()) {
            return "SRC";
        }
        String[] parts = cleaned.split("\\s+");
        if (parts.length == 1) {
            String word = sanitizeWord(parts[0]);
            if (word.isBlank()) {
                return "SRC";
            }
            return word.substring(0, Math.min(2, word.length())).toUpperCase(Locale.ROOT);
        }

        String first = sanitizeWord(parts[0]);
        String second = sanitizeWord(parts[1]);
        if (first.isBlank() && second.isBlank()) {
            return "SRC";
        }
        StringBuilder sb = new StringBuilder();
        if (!first.isBlank()) {
            sb.append(first.charAt(0));
        }
        if (!second.isBlank()) {
            sb.append(second.charAt(0));
        }
        if (sb.isEmpty()) {
            return "SRC";
        }
        return sb.toString().toUpperCase(Locale.ROOT);
    }

    private static String firstWord(String displayName) {
        String[] parts = safe(displayName).trim().split("\\s+");
        if (parts.length == 0) {
            return "";
        }
        return sanitizeWord(parts[0]).toLowerCase(Locale.ROOT);
    }

    private static String sanitizeWord(String word) {
        return safe(word).replaceAll("[^A-Za-z0-9]", "");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String normalizeTheme(String value) {
        String normalized = safe(value).trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? DEFAULT_THEME : normalized;
    }
}