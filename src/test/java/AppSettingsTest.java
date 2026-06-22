import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("AppSettings")
class AppSettingsTest {

    private static AppSettings.SourceProfile profile(String id, String displayName, String shortLabel) {
        return new AppSettings.SourceProfile(id, displayName, shortLabel, "");
    }

    // ----------------------------------------------------------------------------- short labels (positive)

    @Test
    @DisplayName("Explicit short label wins and is upper-cased")
    void explicitShortLabel() {
        Map<String, String> labels = AppSettings.buildEffectiveShortLabels(List.of(profile("1", "London Prod", "px")));
        assertEquals("PX", labels.get("1"));
    }

    @Test
    @DisplayName("Colliding first words collapse to a 3-letter prefix")
    void collidingFirstWords() {
        Map<String, String> labels = AppSettings.buildEffectiveShortLabels(List.of(
                profile("1", "Production East", ""),
                profile("2", "Production West", "")));
        assertEquals("PRO", labels.get("1"));
        assertEquals("PRO", labels.get("2"));
    }

    @Test
    @DisplayName("Multi-word distinct name becomes initials")
    void initialsForDistinctName() {
        Map<String, String> labels = AppSettings.buildEffectiveShortLabels(List.of(profile("1", "London Prod Center", "")));
        assertEquals("LP", labels.get("1"));
    }

    @Test
    @DisplayName("Single-word distinct name uses first two letters")
    void singleWordName() {
        Map<String, String> labels = AppSettings.buildEffectiveShortLabels(List.of(profile("1", "London", "")));
        assertEquals("LO", labels.get("1"));
    }

    // ----------------------------------------------------------------------------- short labels (negative)

    @Test
    @DisplayName("Blank display name falls back to SRC")
    void blankNameFallsBack() {
        Map<String, String> labels = AppSettings.buildEffectiveShortLabels(List.of(profile("1", "   ", "")));
        assertEquals("SRC", labels.get("1"));
    }

    @Test
    @DisplayName("Empty profile list yields empty label map")
    void emptyListNoLabels() {
        assertEquals(Map.of(), AppSettings.buildEffectiveShortLabels(List.of()));
    }

    // ------------------------------------------------------------------------------ active profile handling

    @Test
    @DisplayName("Invalid active profile id is repaired to the first profile")
    void repairsInvalidActiveProfile() {
        AppSettings settings = new AppSettings();
        settings.replaceSourceProfiles(List.of(profile("a", "A", ""), profile("b", "B", "")));
        settings.setActiveProfileId("does-not-exist");
        assertEquals("a", settings.getActiveProfileId());
        assertNotNull(settings.getActiveProfile());
    }

    @Test
    @DisplayName("Active profile is null when there are no profiles")
    void nullActiveWhenEmpty() {
        AppSettings settings = new AppSettings();
        settings.replaceSourceProfiles(List.of());
        assertNull(settings.getActiveProfileId());
        assertNull(settings.getActiveProfile());
    }

    @Test
    @DisplayName("Profiles are capped at MAX_SOURCES")
    void capsAtMaxSources() {
        List<AppSettings.SourceProfile> many = new ArrayList<>();
        for (int i = 0; i < AppSettings.MAX_SOURCES + 5; i++) {
            many.add(profile("id" + i, "Name " + i, ""));
        }
        AppSettings settings = new AppSettings();
        settings.replaceSourceProfiles(many);
        assertEquals(AppSettings.MAX_SOURCES, settings.getSourceProfiles().size());
    }

    @Test
    @DisplayName("Theme is normalized to lower-case with a default fallback")
    void themeNormalization() {
        AppSettings settings = new AppSettings();
        settings.setTheme("DARK");
        assertEquals("dark", settings.getTheme());
        settings.setTheme("  ");
        assertEquals(AppSettings.DEFAULT_THEME, settings.getTheme());
    }
}

