import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AppSettingsStore (persistence)")
class AppSettingsStoreTest {

    // -------------------------------------------------------------------------------------- load (positive)

    @Test
    @DisplayName("Loading a missing settings file creates defaults and writes the file")
    void loadMissingCreatesDefaults(@TempDir Path tmp) {
        Path file = tmp.resolve("app.properties");
        AppSettingsStore store = new AppSettingsStore(file);

        AppSettings settings = store.load();

        assertEquals(AppSettings.DEFAULT_THEME, settings.getTheme());
        assertTrue(settings.getSourceProfiles().isEmpty());
        assertTrue(Files.exists(file));
    }

    @Test
    @DisplayName("Save then load round-trips profiles, theme, active id and default replacement")
    void roundTrip(@TempDir Path tmp) {
        Path file = tmp.resolve("app.properties");
        AppSettingsStore store = new AppSettingsStore(file);

        AppSettings settings = new AppSettings();
        settings.replaceSourceProfiles(List.of(
                new AppSettings.SourceProfile("a", "Alpha", "AL", "C:/alpha"),
                new AppSettings.SourceProfile("b", "Bravo", "", "C:/bravo")));
        settings.setActiveProfileId("b");
        settings.setTheme("dark");
        settings.setDefaultReplacementComponentId("PSM-RDP");
        settings.setOutputRetentionDays(45);
        store.save(settings);

        AppSettings reloaded = store.load();
        assertEquals(2, reloaded.getSourceProfiles().size());
        assertEquals("a", reloaded.getSourceProfiles().get(0).id());
        assertEquals("Alpha", reloaded.getSourceProfiles().get(0).displayName());
        assertEquals("C:/bravo", reloaded.getSourceProfiles().get(1).folderPath());
        assertEquals("b", reloaded.getActiveProfileId());
        assertEquals("dark", reloaded.getTheme());
        assertEquals("PSM-RDP", reloaded.getDefaultReplacementComponentId());
        assertEquals(45, reloaded.getOutputRetentionDays());
    }

    @Test
    @DisplayName("A missing or non-numeric retention value loads as 0 (disabled)")
    void retentionDefaultsToZero(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("app.properties");
        Files.writeString(file, "theme=dark\noutputRetentionDays=not-a-number\n", StandardCharsets.UTF_8);

        AppSettingsStore store = new AppSettingsStore(file);
        AppSettings settings = store.load();

        assertEquals(0, settings.getOutputRetentionDays());
    }

    @Test
    @DisplayName("newProfile generates a dash-free id and keeps the display name")
    void newProfileId() {
        AppSettings.SourceProfile profile = AppSettingsStore.newProfile("My Source");
        assertEquals("My Source", profile.displayName());
        assertFalse(profile.id().contains("-"));
        assertEquals(32, profile.id().length());
    }

    // -------------------------------------------------------------------------------------- load (negative)

    @Test
    @DisplayName("Loading more than MAX_SOURCES profiles caps the list and reports it")
    void capsProfilesAndReports(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("app.properties");
        StringBuilder props = new StringBuilder();
        List<String> ids = new ArrayList<>();
        int total = AppSettings.MAX_SOURCES + 3;
        for (int i = 0; i < total; i++) {
            String id = "id" + i;
            ids.add(id);
            props.append("profile.").append(id).append(".displayName=Name").append(i).append('\n');
            props.append("profile.").append(id).append(".shortLabel=\n");
            props.append("profile.").append(id).append(".folderPath=C:/p").append(i).append('\n');
        }
        props.append("profiles.order=").append(String.join(",", ids)).append('\n');
        Files.writeString(file, props.toString(), StandardCharsets.UTF_8);

        List<String> errors = new ArrayList<>();
        AppSettingsStore store = new AppSettingsStore(file);
        store.setErrorHandler(errors::add);

        AppSettings settings = store.load();

        assertEquals(AppSettings.MAX_SOURCES, settings.getSourceProfiles().size());
        assertTrue(errors.stream().anyMatch(e -> e.contains("source profiles")));
    }

    @Test
    @DisplayName("Junk lines in the settings file are ignored, not fatal")
    void junkFileIsIgnored(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("app.properties");
        Files.writeString(file, "###garbage header\nnot-a-real-key\ntheme=dark\n", StandardCharsets.UTF_8);

        List<String> errors = new ArrayList<>();
        AppSettingsStore store = new AppSettingsStore(file);
        store.setErrorHandler(errors::add);

        AppSettings settings = store.load();
        assertEquals("dark", settings.getTheme());
        assertTrue(settings.getSourceProfiles().isEmpty());
    }

    @Test
    @DisplayName("A malformed \\uXXXX escape degrades to defaults instead of throwing")
    void malformedUnicodeDegrades(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("app.properties");
        // An invalid \\uZZZZ escape makes Properties.load throw IllegalArgumentException.
        Files.writeString(file, "theme=dark\nbroken=\\uZZZZ\n", StandardCharsets.UTF_8);

        List<String> errors = new ArrayList<>();
        AppSettingsStore store = new AppSettingsStore(file);
        store.setErrorHandler(errors::add);

        AppSettings settings = store.load();

        // The read failed and was reported; entries parsed before the bad line may survive, but the
        // load must not throw and must not leave the app in an unusable state.
        assertTrue(errors.stream().anyMatch(e -> e.contains("Failed to read settings")));
        assertTrue(settings.getSourceProfiles().isEmpty());
        assertTrue(settings.getTheme().equals("dark") || settings.getTheme().equals(AppSettings.DEFAULT_THEME));
    }
}


