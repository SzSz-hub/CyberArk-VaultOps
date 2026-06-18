import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Consumer;

public class AppSettingsStore {
    private static final String SETTINGS_FILE = "app.properties";

    private final Path settingsPath;
    private Consumer<String> errorHandler = System.err::println;

    public AppSettingsStore() {
        this(Paths.get(SETTINGS_FILE));
    }

    public AppSettingsStore(Path settingsPath) {
        this.settingsPath = settingsPath;
    }

    public void setErrorHandler(Consumer<String> errorHandler) {
        this.errorHandler = errorHandler == null ? System.err::println : errorHandler;
    }

    private void reportError(String message) {
        errorHandler.accept(message);
    }

    public AppSettings load() {
        AppSettings settings = new AppSettings();
        Properties properties = new Properties();

        if (Files.exists(settingsPath)) {
            try (InputStream in = Files.newInputStream(settingsPath)) {
                properties.load(in);
            } catch (IOException e) {
                reportError("Failed to read settings from " + settingsPath + ": " + describe(e));
            }
        }

        List<AppSettings.SourceProfile> profiles = readProfiles(properties);
        settings.replaceSourceProfiles(profiles);
        settings.setActiveProfileId(properties.getProperty("activeProfile", ""));
        settings.setTheme(properties.getProperty("theme", AppSettings.DEFAULT_THEME));
        settings.ensureValidActiveProfile();

        if (!Files.exists(settingsPath)) {
            save(settings);
        }

        return settings;
    }

    public void save(AppSettings settings) {
        Properties properties = new Properties();

        List<AppSettings.SourceProfile> profiles = settings.getSourceProfiles();
        StringBuilder order = new StringBuilder();
        for (int i = 0; i < profiles.size(); i++) {
            AppSettings.SourceProfile profile = profiles.get(i);
            if (i > 0) {
                order.append(',');
            }
            order.append(profile.id());

            String prefix = "profile." + profile.id() + ".";
            properties.setProperty(prefix + "displayName", valueOrEmpty(profile.displayName()));
            properties.setProperty(prefix + "shortLabel", valueOrEmpty(profile.shortLabel()));
            properties.setProperty(prefix + "folderPath", valueOrEmpty(profile.folderPath()));
        }

        properties.setProperty("profiles.order", order.toString());
        properties.setProperty("activeProfile", valueOrEmpty(settings.getActiveProfileId()));
        properties.setProperty("theme", valueOrEmpty(settings.getTheme()));

        writeAtomically(properties);
    }

    private void writeAtomically(Properties properties) {
        Path target = settingsPath.toAbsolutePath().normalize();
        Path directory = target.getParent();
        if (directory == null) {
            directory = Paths.get(".").toAbsolutePath().normalize();
        }
        Path tempFile = null;
        try {
            Files.createDirectories(directory);
            tempFile = Files.createTempFile(directory, "app", ".properties.tmp");

            try (OutputStream out = Files.newOutputStream(tempFile)) {
                properties.store(out, "CyberArkAdminTool settings");
            }

            try {
                Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException atomicUnsupported) {
                // Some filesystems (e.g. certain network shares) cannot move atomically.
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
            tempFile = null;
        } catch (IOException e) {
            reportError("Failed to save settings to " + target + ": " + describe(e));
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Best-effort cleanup of the orphaned temp file.
                }
            }
        }
    }

    private List<AppSettings.SourceProfile> readProfiles(Properties properties) {
        List<AppSettings.SourceProfile> profiles = new ArrayList<>();

        String order = properties.getProperty("profiles.order", "");
        String[] orderedIds = order.split(",");
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String orderedId : orderedIds) {
            String id = orderedId.trim();
            if (!id.isBlank()) {
                ids.add(id);
            }
        }

        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("profile.") && key.endsWith(".displayName")) {
                String id = key.substring("profile.".length(), key.length() - ".displayName".length());
                if (!id.isBlank()) {
                    ids.add(id);
                }
            }
        }

        for (String id : ids) {
            String prefix = "profile." + id + ".";
            AppSettings.SourceProfile profile = new AppSettings.SourceProfile(
                    id,
                    properties.getProperty(prefix + "displayName", ""),
                    properties.getProperty(prefix + "shortLabel", ""),
                    properties.getProperty(prefix + "folderPath", "")
            );
            profiles.add(profile);
            if (profiles.size() >= AppSettings.MAX_SOURCES) {
                break;
            }
        }

        // M4: do not silently discard profiles beyond the cap — tell the user some were dropped.
        if (ids.size() > AppSettings.MAX_SOURCES) {
            reportError("Only the first " + AppSettings.MAX_SOURCES + " source profiles were loaded; "
                    + (ids.size() - AppSettings.MAX_SOURCES) + " additional profile(s) in app.properties were ignored.");
        }

        return profiles;
    }

    public static AppSettings.SourceProfile newProfile(String displayName) {
        return new AppSettings.SourceProfile(
                UUID.randomUUID().toString().replace("-", ""),
                displayName,
                "",
                ""
        );
    }

    public Path getThemesDirectory() {
        Path absoluteSettingsPath = settingsPath.toAbsolutePath().normalize();
        Path parent = absoluteSettingsPath.getParent();
        return (parent == null ? Paths.get(".").toAbsolutePath().normalize() : parent).resolve("themes");
    }


    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String describe(Exception e) {
        return (e.getMessage() == null || e.getMessage().isBlank()) ? e.getClass().getSimpleName() : e.getMessage();
    }
}