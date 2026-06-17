import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class AppController {

    private static final String PV_CONFIGURATION_FILE = "PVConfiguration.xml";
    private static final String POLICIES_FILE = "Policies.xml";
    private static final DateTimeFormatter LOAD_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UI ui;
    private final AppSettings settings;
    private final Consumer<String> onLoadError;
    private final PVConfigurationParser pvParser = new PVConfigurationParser();
    private final Map<String, EnvironmentLoadState> environmentLoadStates = new HashMap<>();

    private boolean connectionComponentLoaded;
    private boolean psmpLoaded;
    private boolean psmLoaded;
    private boolean usagesLoaded;

    public AppController(UI ui, AppSettings settings, Consumer<String> onLoadError) {
        this.ui = ui;
        this.settings = settings;
        this.onLoadError = onLoadError == null ? message -> {} : onLoadError;
    }

    public void loadAll() {
        refreshStatusIndicators();
        ui.refreshCurrentTabContent();

        // Ensure first paint reports the active tab as loaded even when selection listeners did not fire yet.
        loadActiveTabIfNeeded();
        refreshStatusIndicators();
    }

    public void onSourceProfileChanged() {
        connectionComponentLoaded = false;
        psmpLoaded = false;
        psmLoaded = false;
        usagesLoaded = false;
        ui.clearDataTables();
        refreshStatusIndicators();
    }

    public void invalidateCurrentSelection() {
        String selectedTab = ui.getSelectedTabName();
        if ("Connection Components".equals(selectedTab)
                || "PSMs".equals(selectedTab)
                || "PSMPs".equals(selectedTab)) {
            invalidatePvDataForActiveEnvironment();
        } else if ("Usage".equals(selectedTab)) {
            invalidatePoliciesDataForActiveEnvironment();
        } else {
            onLoadError.accept("Update Current works on data tabs (Connection Components, PSMs, PSMPs, Usage).");
            refreshStatusIndicators();
            return;
        }

        ui.refreshCurrentTabContent();
        loadActiveTabIfNeeded();
        onLoadError.accept("Current tab refreshed from source files.");
        refreshStatusIndicators();
    }

    public void invalidateAllSourcesForActiveEnvironment() {
        invalidatePvDataForActiveEnvironment();
        invalidatePoliciesDataForActiveEnvironment();
        ui.refreshCurrentTabContent();
        loadActiveTabIfNeeded();
        onLoadError.accept("All source data invalidated and active tab reloaded.");
        refreshStatusIndicators();
    }

    public void refreshStatusIndicators() {
        AppSettings.SourceProfile profile = settings.getActiveProfile();
        if (profile == null) {
            ui.setLoadStatus("none", "PVConfiguration.xml: no active source", false, "Policies.xml: no active source", false);
            return;
        }

        EnvironmentLoadState envState = getOrCreateEnvironmentState(profile.id());
        Path folderPath;
        try {
            folderPath = getActiveFolderPath();
        } catch (Exception error) {
            ui.setLoadStatus(displaySource(profile), "PVConfiguration.xml: invalid folder", false, "Policies.xml: invalid folder", false);
            return;
        }

        Path pvPath = folderPath.resolve(PV_CONFIGURATION_FILE);
        Path policiesPath = folderPath.resolve(POLICIES_FILE);

        boolean pvStale = isFileStale(envState.pvConfiguration, pvPath);
        boolean policiesStale = isFileStale(envState.policies, policiesPath);

        String pvLabel = formatLoadStatusLabel(PV_CONFIGURATION_FILE, envState.pvConfiguration, pvStale);
        String policiesLabel = formatLoadStatusLabel(POLICIES_FILE, envState.policies, policiesStale);

        ui.setLoadStatus(displaySource(profile), pvLabel, pvStale, policiesLabel, policiesStale);
    }

    public void showConnectionComponentDetails(PVConfigurationParser.ConnectionComponentEntry entry) {
        if (entry == null || entry.details() == null) {
            return;
        }

        TextArea detailsArea = new TextArea(formatConnectionComponentDetails(entry.details()));
        detailsArea.getStyleClass().add("code-area");
        detailsArea.setEditable(false);
        detailsArea.setWrapText(false);

        BorderPane root = new BorderPane(detailsArea);
        root.getStyleClass().add("details-popup");
        Label title = new Label("Connection Component: " + entry.id());
        title.getStyleClass().add("details-title");
        root.setTop(title);

        Stage popup = new Stage();
        popup.setTitle("Connection Component Details");
        Scene scene = new Scene(root, 900, 700);
        ui.applyTheme(scene);
        popup.setScene(scene);
        popup.show();
    }

    public void loadConnectionComponentIfNeeded() {
        if (connectionComponentLoaded || ui.getConnectionComponentTable() == null) {
            return;
        }

        try {
            String pvConfigPath = getActivePvConfigurationPath();
            ObservableList<PVConfigurationParser.ConnectionComponentEntry> masterData =
                    FXCollections.observableArrayList(pvParser.GetConnectionComponents(pvConfigPath));
            wireFiltering(ui.getConnectionComponentTable(), masterData);
            connectionComponentLoaded = true;
            markFileLoaded(PV_CONFIGURATION_FILE, Paths.get(pvConfigPath));
            refreshStatusIndicators();
        } catch (Exception e) {
            reportLoadError("connection components", e);
        }
    }

    public void loadPSMPServersIfNeeded() {
        if (psmpLoaded || ui.getPsmpTable() == null) {
            return;
        }

        try {
            String pvConfigPath = getActivePvConfigurationPath();
            ObservableList<PVConfigurationParser.PSMPServerEntry> masterData =
                    FXCollections.observableArrayList(pvParser.getPSMPServers(pvConfigPath));
            wireFiltering(ui.getPsmpTable(), masterData);
            psmpLoaded = true;
            markFileLoaded(PV_CONFIGURATION_FILE, Paths.get(pvConfigPath));
            refreshStatusIndicators();
        } catch (Exception e) {
            reportLoadError("PSMP servers", e);
        }
    }

    public void loadPSMServersIfNeeded() {
        if (psmLoaded || ui.getPsmTable() == null) {
            return;
        }

        try {
            String pvConfigPath = getActivePvConfigurationPath();
            ObservableList<PVConfigurationParser.PSMServerEntry> masterData =
                    FXCollections.observableArrayList(pvParser.getPSMServers(pvConfigPath));
            wireFiltering(ui.getPsmTable(), masterData);
            psmLoaded = true;
            markFileLoaded(PV_CONFIGURATION_FILE, Paths.get(pvConfigPath));
            refreshStatusIndicators();
        } catch (Exception e) {
            reportLoadError("PSM servers", e);
        }
    }

    public void loadUsageIfNeeded() {
        if (usagesLoaded || ui.getUsageTable() == null) {
            return;
        }

        try {
            String policiesPath = getActivePoliciesPath();
            ObservableList<PoliciesParser.usageEntry> masterData =
                    FXCollections.observableArrayList(new PoliciesParser().getUsage(policiesPath));
            wireFiltering(ui.getUsageTable(), masterData);
            usagesLoaded = true;
            markFileLoaded(POLICIES_FILE, Paths.get(policiesPath));
            refreshStatusIndicators();
        } catch (Exception e) {
            reportLoadError("Usage", e);
        }
    }

    private void reportLoadError(String target, Exception error) {
        AppSettings.SourceProfile profile = settings.getActiveProfile();
        String profileName = profile == null || profile.displayName() == null || profile.displayName().isBlank()
                ? "selected source"
                : profile.displayName();
        String details = (error == null || error.getMessage() == null || error.getMessage().isBlank())
                ? "Check folder path and file availability."
                : error.getMessage();
        onLoadError.accept("Cannot load " + target + " from " + profileName + ": " + details);
    }

    public String getActivePoliciesPath() {
        return getActiveFolderPath().resolve(POLICIES_FILE).toString();
    }

    private String getActivePvConfigurationPath() {
        return getActiveFolderPath().resolve(PV_CONFIGURATION_FILE).toString();
    }

    private Path getActiveFolderPath() {
        AppSettings.SourceProfile profile = settings.getActiveProfile();
        if (profile == null) {
            throw new IllegalStateException("No source profile is selected.");
        }
        if (profile.folderPath() == null || profile.folderPath().isBlank()) {
            throw new IllegalStateException("Source profile '" + profile.displayName() + "' has no folder configured.");
        }
        return Paths.get(profile.folderPath()).normalize();
    }

    private void invalidatePvDataForActiveEnvironment() {
        connectionComponentLoaded = false;
        psmpLoaded = false;
        psmLoaded = false;

        if (ui.getConnectionComponentTable() != null) {
            ui.getConnectionComponentTable().setItems(FXCollections.observableArrayList());
        }
        if (ui.getPsmTable() != null) {
            ui.getPsmTable().setItems(FXCollections.observableArrayList());
        }
        if (ui.getPsmpTable() != null) {
            ui.getPsmpTable().setItems(FXCollections.observableArrayList());
        }

        AppSettings.SourceProfile profile = settings.getActiveProfile();
        if (profile != null) {
            getOrCreateEnvironmentState(profile.id()).pvConfiguration.clear();
        }
    }

    private void invalidatePoliciesDataForActiveEnvironment() {
        usagesLoaded = false;
        if (ui.getUsageTable() != null) {
            ui.getUsageTable().setItems(FXCollections.observableArrayList());
        }

        AppSettings.SourceProfile profile = settings.getActiveProfile();
        if (profile != null) {
            getOrCreateEnvironmentState(profile.id()).policies.clear();
        }
    }

    private void markFileLoaded(String fileName, Path filePath) {
        AppSettings.SourceProfile profile = settings.getActiveProfile();
        if (profile == null) {
            return;
        }

        EnvironmentLoadState state = getOrCreateEnvironmentState(profile.id());
        FileLoadState fileLoadState = PV_CONFIGURATION_FILE.equals(fileName)
                ? state.pvConfiguration
                : state.policies;

        fileLoadState.loadedAt = LocalDateTime.now();
        fileLoadState.sourceModifiedAtLoad = readLastModified(filePath);
    }

    private EnvironmentLoadState getOrCreateEnvironmentState(String profileId) {
        return environmentLoadStates.computeIfAbsent(profileId == null ? "" : profileId, id -> new EnvironmentLoadState());
    }

    private boolean isFileStale(FileLoadState state, Path sourceFile) {
        if (state == null || state.loadedAt == null || state.sourceModifiedAtLoad == null) {
            return false;
        }
        FileTime currentModified = readLastModified(sourceFile);
        return currentModified != null && currentModified.compareTo(state.sourceModifiedAtLoad) > 0;
    }

    private FileTime readLastModified(Path path) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        try {
            return Files.getLastModifiedTime(path);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String formatLoadStatusLabel(String fileName, FileLoadState state, boolean stale) {
        if (state == null || state.loadedAt == null) {
            return fileName + ": never loaded";
        }

        String text = fileName + ": loaded " + LOAD_TIME_FORMATTER.format(state.loadedAt);
        if (stale) {
            return text + " (newer file detected)";
        }
        return text;
    }

    private String displaySource(AppSettings.SourceProfile profile) {
        if (profile == null || profile.displayName() == null || profile.displayName().isBlank()) {
            return "none";
        }
        return profile.displayName();
    }

    private void loadActiveTabIfNeeded() {
        String selectedTab = ui.getSelectedTabName();
        if ("PSMs".equals(selectedTab)) {
            loadPSMServersIfNeeded();
        } else if ("PSMPs".equals(selectedTab)) {
            loadPSMPServersIfNeeded();
        } else if ("Usage".equals(selectedTab)) {
            loadUsageIfNeeded();
        } else {
            loadConnectionComponentIfNeeded();
        }
    }

    private <T> void wireFiltering(TableView<T> table, ObservableList<T> masterData) {
        FilteredList<T> filteredData = new FilteredList<>(masterData, p -> true);

        for (TableColumn<T, ?> col : table.getColumns()) {
            if (col.getUserData() instanceof TextField tf) {
                tf.textProperty().addListener((obs, oldVal, newVal) ->
                        filteredData.setPredicate(entry -> columnFiltersMatch(entry, table)));
            }
        }

        SortedList<T> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sortedData);
    }

    private <T> boolean columnFiltersMatch(T entry, TableView<T> table) {
        for (TableColumn<T, ?> col : table.getColumns()) {
            if (!(col.getUserData() instanceof TextField tf)) {
                continue;
            }

            String filter = tf.getText();
            if (filter == null || filter.isEmpty()) {
                continue;
            }

            var observable = col.getCellObservableValue(entry);
            if (observable == null) {
                return false;
            }

            String cellValue = observable.getValue() == null ? "" : observable.getValue().toString();
            if (!cellValue.toLowerCase().contains(filter.toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    private String formatConnectionComponentDetails(PVConfigurationParser.XmlNode node) {
        StringBuilder sb = new StringBuilder();

        appendAttributes(sb, node.attributes(), 0);
        for (PVConfigurationParser.XmlNode child : node.children()) {
            appendNode(sb, child, 0);
        }

        return sb.toString();
    }

    private void appendNode(StringBuilder sb, PVConfigurationParser.XmlNode node, int indent) {
        String pad = "\t".repeat(indent);
        sb.append(pad).append(node.name()).append('\n');
        appendAttributes(sb, node.attributes(), indent + 1);
        for (PVConfigurationParser.XmlNode child : node.children()) {
            appendNode(sb, child, indent + 1);
        }
    }

    private void appendAttributes(StringBuilder sb, Map<String, String> attributes, int indent) {
        String pad = "\t".repeat(indent);
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            sb.append(pad)
                    .append(entry.getKey())
                    .append("\t=\t")
                    .append(entry.getValue())
                    .append('\n');
        }
    }

    private static class EnvironmentLoadState {
        private final FileLoadState pvConfiguration = new FileLoadState();
        private final FileLoadState policies = new FileLoadState();
    }

    private static class FileLoadState {
        private LocalDateTime loadedAt;
        private FileTime sourceModifiedAtLoad;

        private void clear() {
            loadedAt = null;
            sourceModifiedAtLoad = null;
        }
    }
}
