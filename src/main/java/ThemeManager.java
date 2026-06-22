import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Stream;

public final class ThemeManager {
    private static final String THEMES_INDEX = "/themes/themes.properties";
    private static final String BASE_THEME_FILE = "base.css";
    private static final String EXAMPLE_THEME_FILE = "example.css";

    private final Path externalThemesDirectory;

    private List<ThemeOption> cachedThemes;
    private boolean exampleTemplateEnsured;

    public ThemeManager(Path externalThemesDirectory) {
        this.externalThemesDirectory = externalThemesDirectory == null
                ? Path.of("themes").toAbsolutePath().normalize()
                : externalThemesDirectory.toAbsolutePath().normalize();
    }

    public Path externalThemesDirectory() {
        return externalThemesDirectory;
    }

    public void refreshThemes() {
        cachedThemes = null;
    }

    public List<ThemeOption> discoverThemes() {
        if (cachedThemes != null) {
            return cachedThemes;
        }
        Properties themeIndex = loadThemeIndex();
        Map<String, ThemeOption> themes = new LinkedHashMap<>();
        loadPackagedThemes(themes, themeIndex);
        loadExternalThemes(themes);
        cachedThemes = new ArrayList<>(themes.values());
        return cachedThemes;
    }

    public ThemeOption resolveTheme(String themeId, List<ThemeOption> themes) {
        if (themes == null || themes.isEmpty()) {
            return null;
        }

        String normalizedId = normalizeThemeId(themeId);
        for (ThemeOption theme : themes) {
            if (theme.id().equals(normalizedId)) {
                return theme;
            }
        }
        for (ThemeOption theme : themes) {
            if (AppSettings.DEFAULT_THEME.equals(theme.id())) {
                return theme;
            }
        }
        return themes.get(0);
    }

    public List<String> buildStylesheetUris(ThemeOption theme) {
        if (theme == null) {
            return List.of();
        }
        if (Objects.equals(theme.baseStylesheetUri(), theme.stylesheetUri())) {
            return List.of(theme.stylesheetUri());
        }
        return List.of(theme.baseStylesheetUri(), theme.stylesheetUri());
    }

    public String packagedBaseStylesheetUri() {
        URL resource = getClass().getResource("/themes/" + BASE_THEME_FILE);
        return resource == null ? "" : resource.toExternalForm();
    }

    private void loadPackagedThemes(Map<String, ThemeOption> themes, Properties properties) {
        String packagedBaseUri = packagedBaseStylesheetUri();
        String[] orderedIds = properties.getProperty("order", "").split(",");
        for (String rawId : orderedIds) {
            String id = normalizeThemeId(rawId);
            if (id.isBlank()) {
                continue;
            }

            String fileName = properties.getProperty(id + ".file", id + ".css").trim();
            URL resource = getClass().getResource("/themes/" + fileName);
            if (resource == null) {
                continue;
            }

            String displayName = properties.getProperty(id + ".name", toDisplayName(id)).trim();
            themes.put(id, new ThemeOption(id, displayName, resource.toExternalForm(), packagedBaseUri, false));
        }
    }

    private void loadExternalThemes(Map<String, ThemeOption> themes) {
        try {
            Files.createDirectories(externalThemesDirectory);
            ensureExampleThemeTemplate();
        } catch (IOException e) {
            return;
        }

        String packagedBaseUri = packagedBaseStylesheetUri();

        try (Stream<Path> stream = Files.list(externalThemesDirectory)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".css"))
                    .filter(path -> !BASE_THEME_FILE.equalsIgnoreCase(path.getFileName().toString()))
                    .filter(path -> !EXAMPLE_THEME_FILE.equalsIgnoreCase(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        String id = normalizeThemeId(stripExtension(fileName));
                        if (id.isBlank()) {
                            return;
                        }
                        themes.put(id, new ThemeOption(id, toDisplayName(stripExtension(fileName)), path.toUri().toString(), packagedBaseUri, true));
                    });
        } catch (IOException ignored) {
        }
    }

    private Properties loadThemeIndex() {
        Properties properties = new Properties();
        try (InputStream input = getClass().getResourceAsStream(THEMES_INDEX)) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException ignored) {
        }
        return properties;
    }

    private void ensureExampleThemeTemplate() {
        if (exampleTemplateEnsured) {
            return;
        }
        exampleTemplateEnsured = true;

        Path exampleThemePath = externalThemesDirectory.resolve(EXAMPLE_THEME_FILE);
        if (Files.exists(exampleThemePath)) {
            return;
        }

        String template = """
                /*
                 * example.css
                 *
                 * Copy this file and rename it (for example: my-team.css).
                 * Then use Settings > Theme > Refresh themes.
                 */
                .root {
                    -app-bg: #101622;
                    -app-surface: #1a2333;
                    -app-surface-alt: #222f45;
                    -app-muted: #273754;
                    -app-border: #344a6a;
                    -app-text: #eef4ff;
                    -app-subtle-text: #97a8c4;
                    -app-accent: #4da3ff;
                    -app-accent-soft: #3b87d7;
                    -app-text-on-accent: #ffffff;
                    -app-selection: #2f476d;
                    -app-hover: #2a3a55;
                    -app-row-alt: #162133;
                    -app-toast-bg: rgba(11, 17, 28, 0.96);
                    -app-toast-text: #f7f9ff;

                    /* Compare result highlights (yellow = different, green = only left, red = only right). */
                    -app-compare-diff: #4a3f17;
                    -app-compare-only-a: #1f3d28;
                    -app-compare-only-b: #4a2329;
                    -app-compare-diff-selected: #6b5a20;
                    -app-compare-only-a-selected: #2c5739;
                    -app-compare-only-b-selected: #6a3139;
                    -app-compare-text: #eef4ff;
                }
                """;

        try {
            Files.writeString(exampleThemePath, template, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static String stripExtension(String value) {
        int dotIndex = value.lastIndexOf('.');
        return dotIndex >= 0 ? value.substring(0, dotIndex) : value;
    }

    private static String toDisplayName(String value) {
        String normalized = normalizeThemeId(value).replace('-', ' ');
        if (normalized.isBlank()) {
            return "Theme";
        }

        String[] parts = normalized.split("\\s+");
        StringBuilder displayName = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!displayName.isEmpty()) {
                displayName.append(' ');
            }
            displayName.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                displayName.append(part.substring(1));
            }
        }
        return displayName.toString();
    }

    public static String normalizeThemeId(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replace('_', '-').replace(' ', '-');
        normalized = normalized.replaceAll("[^a-z0-9-]", "");
        normalized = normalized.replaceAll("-+", "-");
        return normalized;
    }

    public record ThemeOption(String id, String displayName, String stylesheetUri, String baseStylesheetUri,
                              boolean external) {
    }
}