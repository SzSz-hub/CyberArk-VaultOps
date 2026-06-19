import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class AppController {

    private static final String PV_CONFIGURATION_FILE = "PVConfiguration.xml";
    private static final String POLICIES_FILE = "Policies.xml";
    private static final DateTimeFormatter LOAD_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UI ui;
    private final AppSettings settings;
    private final Consumer<String> onLoadError;
    private final PVConfigurationParser pvParser = new PVConfigurationParser();
    private final PoliciesParser policiesParser = new PoliciesParser();
    private final ComponentOperations componentOperations = new ComponentOperations();
    private final Map<String, EnvironmentLoadState> environmentLoadStates = new HashMap<>();

    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "VaultOps-loader");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<TableView<?>, FilterBinding> filterBindings = new IdentityHashMap<>();

    private final Map<String, Stage> detailWindows = new HashMap<>();

    private boolean connectionComponentLoaded;
    private boolean psmpLoaded;
    private boolean psmLoaded;
    private boolean usagesLoaded;
    private boolean policiesLoaded;
    private boolean targetsLoaded;

    public AppController(UI ui, AppSettings settings, Consumer<String> onLoadError) {
        this.ui = ui;
        this.settings = settings;
        this.onLoadError = onLoadError == null ? message -> {} : onLoadError;
    }

    public void shutdown() {
        backgroundExecutor.shutdownNow();
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private <T> void runAsync(String target, ThrowingSupplier<T> work, Consumer<T> onSuccess, Runnable onFailure) {
        submitBackground(() -> {
            try {
                T result = work.get();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Exception error) {
                Platform.runLater(() -> {
                    if (onFailure != null) {
                        onFailure.run();
                    }
                    reportLoadError(target, error);
                });
            }
        });
    }

    /** Submits to the background worker, tolerating shutdown races during application close. */
    private void submitBackground(Runnable task) {
        try {
            backgroundExecutor.submit(task);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // Executor already shutting down; nothing to do.
        }
    }

    public void loadAll() {
        refreshStatusIndicators();
        ui.refreshCurrentTabContent();

        loadActiveTabIfNeeded();
        refreshStatusIndicators();
    }

    public void onSourceProfileChanged() {
        connectionComponentLoaded = false;
        psmpLoaded = false;
        psmLoaded = false;
        usagesLoaded = false;
        policiesLoaded = false;
        targetsLoaded = false;
        ui.clearDataTables();
        refreshStatusIndicators();
    }

    public void invalidateCurrentSelection() {
        String selectedTab = ui.getSelectedTabName();
        if (UI.TAB_CONNECTION_COMPONENTS.equals(selectedTab)
                || UI.TAB_PSMS.equals(selectedTab)
                || UI.TAB_PSMPS.equals(selectedTab)) {
            invalidatePvDataForActiveEnvironment();
        } else if (UI.TAB_USAGES.equals(selectedTab)
                || UI.TAB_POLICIES.equals(selectedTab)
                || UI.TAB_ALTER_ADDRESSES.equals(selectedTab)) {
            invalidatePoliciesDataForActiveEnvironment();
        } else {
            onLoadError.accept("Update Current works on data tabs (Connection Components, Policies, Alter Addresses, Usages, PSMs, PSMPs).");
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

        String sourceName = displaySource(profile);
        LocalDateTime pvLoadedAt = envState.pvConfiguration.loadedAt;
        FileTime pvModifiedAtLoad = envState.pvConfiguration.sourceModifiedAtLoad;
        LocalDateTime policiesLoadedAt = envState.policies.loadedAt;
        FileTime policiesModifiedAtLoad = envState.policies.sourceModifiedAtLoad;

        submitBackground(() -> {
            boolean pvStale = isStale(pvLoadedAt, pvModifiedAtLoad, readLastModified(pvPath));
            boolean policiesStale = isStale(policiesLoadedAt, policiesModifiedAtLoad, readLastModified(policiesPath));
            String pvLabel = formatLoadStatusLabel(PV_CONFIGURATION_FILE, pvLoadedAt, pvStale);
            String policiesLabel = formatLoadStatusLabel(POLICIES_FILE, policiesLoadedAt, policiesStale);
            Platform.runLater(() ->
                    ui.setLoadStatus(sourceName, pvLabel, pvStale, policiesLabel, policiesStale));
        });
    }

    public void showConnectionComponentDetails(PVConfigurationParser.ConnectionComponentEntry entry) {
        if (entry == null || entry.details() == null) {
            return;
        }

        showDetailWindow(
                "CC|" + activeSourceName() + "|" + entry.id(),
                "Connection Component Details",
                "Connection Component: " + entry.id(),
                formatConnectionComponentDetails(entry.details()));
    }

    public void loadConnectionComponentIfNeeded() {
        if (connectionComponentLoaded || ui.getConnectionComponentTable() == null) {
            return;
        }

        String pvConfigPath;
        String policiesPath;
        try {
            pvConfigPath = getActivePvConfigurationPath();
            policiesPath = getActivePoliciesPath();
        } catch (Exception e) {
            reportLoadError("connection components", e);
            return;
        }

        connectionComponentLoaded = true;

        runAsync("connection components",
                () -> buildConnectionComponents(pvConfigPath, policiesPath),
                result -> {
                    ObservableList<PVConfigurationParser.ConnectionComponentEntry> masterData =
                            FXCollections.observableArrayList(result.components());
                    wireFiltering(ui.getConnectionComponentTable(), masterData);
                    markFileLoaded(PV_CONFIGURATION_FILE, Paths.get(pvConfigPath));
                    // M3: counts are derived from Policies.xml, so track its staleness too.
                    if (result.policiesRead()) {
                        markFileLoaded(POLICIES_FILE, Paths.get(policiesPath));
                    }
                    refreshStatusIndicators();
                },
                () -> connectionComponentLoaded = false);
    }

    private ConnectionComponentsResult buildConnectionComponents(String pvConfigPath, String policiesPath) throws Exception {
        List<PVConfigurationParser.ConnectionComponentEntry> components = pvParser.GetConnectionComponents(pvConfigPath);

        Map<String, Integer> assignmentCounts = new HashMap<>();
        boolean policiesRead = false;
        try {
            List<PoliciesParser.PolicyEntry> policies = policiesParser.getPolicies(policiesPath);
            policiesRead = true;
            for (PoliciesParser.PolicyEntry policy : policies) {
                String assignedCompStr = policy.assignedComponents();
                if (assignedCompStr != null && !assignedCompStr.isBlank()) {
                    String[] compIds = assignedCompStr.split(",");
                    for (String compId : compIds) {
                        String trimmedId = compId.trim();
                        if (!trimmedId.isBlank()) {
                            assignmentCounts.put(trimmedId, assignmentCounts.getOrDefault(trimmedId, 0) + 1);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // If policies can't be loaded, counts default to 0 and staleness is not tracked.
        }

        List<PVConfigurationParser.ConnectionComponentEntry> componentsWithCounts = new ArrayList<>();
        for (PVConfigurationParser.ConnectionComponentEntry comp : components) {
            int count = assignmentCounts.getOrDefault(comp.id(), 0);
            componentsWithCounts.add(new PVConfigurationParser.ConnectionComponentEntry(
                    comp.id(), comp.name(), comp.ClientApp(), comp.ClientDispatcher(), count, comp.details()
            ));
        }
        return new ConnectionComponentsResult(componentsWithCounts, policiesRead);
    }

    private record ConnectionComponentsResult(
            List<PVConfigurationParser.ConnectionComponentEntry> components,
            boolean policiesRead) {
    }

    public void loadPSMPServersIfNeeded() {
        if (psmpLoaded || ui.getPsmpTable() == null) {
            return;
        }

        String pvConfigPath;
        try {
            pvConfigPath = getActivePvConfigurationPath();
        } catch (Exception e) {
            reportLoadError("PSMP servers", e);
            return;
        }
        psmpLoaded = true;

        runAsync("PSMP servers",
                () -> pvParser.getPSMPServers(pvConfigPath),
                data -> {
                    wireFiltering(ui.getPsmpTable(), FXCollections.observableArrayList(data));
                    markFileLoaded(PV_CONFIGURATION_FILE, Paths.get(pvConfigPath));
                    refreshStatusIndicators();
                },
                () -> psmpLoaded = false);
    }

    public void loadPSMServersIfNeeded() {
        if (psmLoaded || ui.getPsmTable() == null) {
            return;
        }

        String pvConfigPath;
        try {
            pvConfigPath = getActivePvConfigurationPath();
        } catch (Exception e) {
            reportLoadError("PSM servers", e);
            return;
        }
        psmLoaded = true;

        runAsync("PSM servers",
                () -> pvParser.getPSMServers(pvConfigPath),
                data -> {
                    wireFiltering(ui.getPsmTable(), FXCollections.observableArrayList(data));
                    markFileLoaded(PV_CONFIGURATION_FILE, Paths.get(pvConfigPath));
                    refreshStatusIndicators();
                },
                () -> psmLoaded = false);
    }

    public void loadUsageIfNeeded() {
        if (usagesLoaded || ui.getUsageTable() == null) {
            return;
        }

        String policiesPath;
        try {
            policiesPath = getActivePoliciesPath();
        } catch (Exception e) {
            reportLoadError("Usage", e);
            return;
        }
        usagesLoaded = true;

        runAsync("Usage",
                () -> policiesParser.getUsage(policiesPath),
                data -> {
                    wireFiltering(ui.getUsageTable(), FXCollections.observableArrayList(data));
                    markFileLoaded(POLICIES_FILE, Paths.get(policiesPath));
                    refreshStatusIndicators();
                },
                () -> usagesLoaded = false);
    }

    public void loadPoliciesIfNeeded() {
        if (policiesLoaded || ui.getPoliciesTable() == null) {
            return;
        }

        String policiesPath;
        try {
            policiesPath = getActivePoliciesPath();
        } catch (Exception e) {
            reportLoadError("Policies", e);
            return;
        }
        policiesLoaded = true;

        runAsync("Policies",
                () -> policiesParser.getPolicies(policiesPath),
                data -> {
                    wireFiltering(ui.getPoliciesTable(), FXCollections.observableArrayList(data));
                    markFileLoaded(POLICIES_FILE, Paths.get(policiesPath));
                    refreshStatusIndicators();
                },
                () -> policiesLoaded = false);
    }

    public void loadTargetsIfNeeded() {
        if (targetsLoaded || ui.getAlteredAddressTable() == null) {
            return;
        }

        String pvConfigPath;
        try {
            pvConfigPath = getActivePvConfigurationPath();
        } catch (Exception e) {
            reportLoadError("altered addresses", e);
            return;
        }
        targetsLoaded = true;

        runAsync("altered addresses",
                () -> policiesParser.getAggregatedTargetsByAlteredAddress(pvConfigPath),
                data -> {
                    wireFiltering(ui.getAlteredAddressTable(), FXCollections.observableArrayList(data));
                    markFileLoaded(PV_CONFIGURATION_FILE, Paths.get(pvConfigPath));
                    refreshStatusIndicators();
                },
                () -> targetsLoaded = false);
    }

    public void onAlteredAddressSelected(String address) {
        if (address == null || address.isBlank()) {
            ui.setTargetDetails(List.of());
            return;
        }

        String pvConfigPath;
        try {
            pvConfigPath = getActivePvConfigurationPath();
        } catch (Exception e) {
            ui.setTargetDetails(List.of());
            reportLoadError("target details", e);
            return;
        }

        runAsync("target details",
                () -> policiesParser.getTargetDetailsForAddress(pvConfigPath, address),
                details -> {
                    ui.setTargetDetails(details);
                    markFileLoaded(PV_CONFIGURATION_FILE, Paths.get(pvConfigPath));
                    refreshStatusIndicators();
                },
                () -> ui.setTargetDetails(List.of()));
    }

    public void onConnectionComponentSelected(PVConfigurationParser.ConnectionComponentEntry entry) {
        if (entry == null) {
            ui.setConnectionAssignments(List.of());
            return;
        }

        String policiesPath;
        try {
            policiesPath = getActivePoliciesPath();
        } catch (Exception e) {
            ui.setConnectionAssignments(List.of());
            reportLoadError("component assignments", e);
            return;
        }

        runAsync("component assignments",
                () -> policiesParser.getAssignmentsForConnectionComponent(policiesPath, entry.id()),
                rows -> {
                    ui.setConnectionAssignments(rows);
                    markFileLoaded(POLICIES_FILE, Paths.get(policiesPath));
                    refreshStatusIndicators();
                },
                () -> ui.setConnectionAssignments(List.of()));
    }


    // ------------------------------------------------------------------------------------------ Export

    public void exportConnectionComponents(List<PVConfigurationParser.ConnectionComponentEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            onLoadError.accept("Select at least one connection component to export.");
            return;
        }

        String pvConfigPath;
        try {
            pvConfigPath = getActivePvConfigurationPath();
        } catch (Exception e) {
            reportLoadError("connection component export", e);
            return;
        }

        Path defaultRoot = Paths.get("exports").toAbsolutePath().normalize();
        try {
            Files.createDirectories(defaultRoot);
        } catch (Exception ignored) {
            // The exports folder is only a suggested starting point for the chooser.
        }

        File chosen = ui.chooseDirectory("Choose export destination folder", defaultRoot.toFile());
        if (chosen == null) {
            return; // User cancelled the chooser.
        }
        Path destinationRoot = chosen.toPath();

        List<String> ids = new ArrayList<>();
        for (PVConfigurationParser.ConnectionComponentEntry entry : entries) {
            if (entry != null && entry.id() != null && !entry.id().isBlank()) {
                ids.add(entry.id());
            }
        }

        runAsync("connection component export",
                () -> {
                    List<ComponentOperations.ExportResult> results = new ArrayList<>();
                    for (String id : ids) {
                        results.add(componentOperations.exportConnectionComponent(pvConfigPath, id, destinationRoot));
                    }
                    return results;
                },
                results -> {
                    if (results.size() == 1) {
                        onLoadError.accept("Exported " + results.get(0).componentId() + " to " + results.get(0).zipPath());
                    } else {
                        onLoadError.accept("Exported " + results.size() + " connection components to " + destinationRoot);
                    }
                },
                null);
    }

    // ------------------------------------------------------------------------------ Remove / Unlink

    public void removeConnectionComponents(List<PVConfigurationParser.ConnectionComponentEntry> entries) {
        performComponentRemoval(entries, true);
    }

    public void unlinkConnectionComponents(List<PVConfigurationParser.ConnectionComponentEntry> entries) {
        performComponentRemoval(entries, false);
    }

    private void performComponentRemoval(List<PVConfigurationParser.ConnectionComponentEntry> entries, boolean alsoRemoveDefinitions) {
        if (entries == null || entries.isEmpty()) {
            onLoadError.accept("Select at least one connection component first.");
            return;
        }

        String policiesPath;
        String pvConfigPath;
        try {
            policiesPath = getActivePoliciesPath();
            pvConfigPath = getActivePvConfigurationPath();
        } catch (Exception e) {
            reportLoadError("connection component change", e);
            return;
        }

        List<String> ids = collectComponentIds(entries);
        if (ids.isEmpty()) {
            onLoadError.accept("Select at least one connection component first.");
            return;
        }

        String title = alsoRemoveDefinitions ? "Remove connection components" : "Unlink connection components";
        String message = alsoRemoveDefinitions
                ? "Remove " + ids.size() + " connection component(s)?\n\n" + String.join(", ", ids)
                        + "\n\nThis unlinks them from every policy in Policies.xml AND deletes their definitions "
                        + "from PVConfiguration.xml. The source files are not modified; updated copies and a "
                        + "changelog are written to the output folder."
                : "Unlink " + ids.size() + " connection component(s) from all policies?\n\n" + String.join(", ", ids)
                        + "\n\nThe PVConfiguration.xml definitions are kept. The source files are not modified; an "
                        + "updated Policies.xml and a changelog are written to the output folder.";
        if (!ui.confirm(title, message)) {
            return;
        }

        List<String> dropdownIds;
        try {
            dropdownIds = loadConnectionComponentIds(pvConfigPath);
        } catch (Exception e) {
            dropdownIds = new ArrayList<>();
        }
        final List<String> resolverIds = dropdownIds;

        Path outputRoot = Paths.get("output").toAbsolutePath().normalize();
        String sourceLabel = activeSourceName();
        String sourceFolder = activeSourceFolder();

        try {
            ComponentOperations.RemovalResult result = alsoRemoveDefinitions
                    ? componentOperations.removeConnectionComponents(policiesPath, pvConfigPath, ids, outputRoot,
                            sourceLabel, sourceFolder, policyId -> ui.showEmptyPolicyDialog(policyId, resolverIds))
                    : componentOperations.unlinkConnectionComponents(policiesPath, ids, outputRoot,
                            sourceLabel, sourceFolder, policyId -> ui.showEmptyPolicyDialog(policyId, resolverIds));

            if (result.cancelled()) {
                onLoadError.accept("Operation cancelled. No files were written.");
                return;
            }

            String summary = "Removed " + result.totalRemovedAssignments() + " policy assignment(s)";
            if (alsoRemoveDefinitions) {
                summary += " and " + result.removedDefinitions() + " definition(s)";
            }
            summary += ". Output written to " + result.outputPolicies().getParent();
            onLoadError.accept(summary);
        } catch (Exception e) {
            reportLoadError("connection component change", e);
        }
    }

    private List<String> collectComponentIds(List<PVConfigurationParser.ConnectionComponentEntry> entries) {
        List<String> ids = new ArrayList<>();
        for (PVConfigurationParser.ConnectionComponentEntry entry : entries) {
            if (entry != null && entry.id() != null && !entry.id().isBlank()) {
                ids.add(entry.id());
            }
        }
        return ids;
    }

    private List<String> loadConnectionComponentIds(String pvConfigPath) throws Exception {
        List<String> ids = new ArrayList<>();
        for (PVConfigurationParser.ConnectionComponentEntry comp : pvParser.GetConnectionComponents(pvConfigPath)) {
            if (comp.id() != null && !comp.id().isBlank()) {
                ids.add(comp.id());
            }
        }
        return ids;
    }

    private String activeSourceFolder() {
        try {
            return getActiveFolderPath().toString();
        } catch (Exception e) {
            return "";
        }
    }

    public void onPolicySelected(PoliciesParser.PolicyEntry policy) {
        if (policy == null) {
            ui.setPolicyAssignments(List.of());
            return;
        }

        String policiesPath;
        try {
            policiesPath = getActivePoliciesPath();
        } catch (Exception e) {
            ui.setPolicyAssignments(List.of());
            reportLoadError("policy components", e);
            return;
        }

        runAsync("policy components",
                () -> policiesParser.getComponentsForPolicy(policiesPath, policy.policyId()),
                ui::setPolicyAssignments,
                () -> ui.setPolicyAssignments(List.of()));
    }

    public void onUsageSelected(PoliciesParser.usageEntry usage) {
        if (usage == null) {
            ui.setUsagePolicyAssignments(List.of());
            return;
        }

        String policiesPath;
        try {
            policiesPath = getActivePoliciesPath();
        } catch (Exception e) {
            ui.setUsagePolicyAssignments(List.of());
            reportLoadError("usage policies", e);
            return;
        }

        runAsync("usage policies",
                () -> policiesParser.getPoliciesForUsage(policiesPath, usage.usageId()),
                ui::setUsagePolicyAssignments,
                () -> ui.setUsagePolicyAssignments(List.of()));
    }

    public void showPolicyDetails(PoliciesParser.PolicyEntry policy) {
        if (policy == null || policy.details() == null) {
            return;
        }

        showDetailWindow(
                "POLICY|" + activeSourceName() + "|" + policy.policyId(),
                "Policy Details",
                "Policy: " + policy.policyId(),
                formatConnectionComponentDetails(policy.details()));
    }

    public void showUsageDetails(PoliciesParser.usageEntry usage) {
        if (usage == null || usage.children() == null || usage.children().isEmpty()) {
            return;
        }

        showDetailWindow(
                "USAGE|" + activeSourceName() + "|" + usage.usageId(),
                "Usage Details",
                "Usage: " + usage.usageId(),
                formatConnectionComponentDetails(usage.children().get(0)));
    }

    public void showConnectionAssignmentDetails(PoliciesParser.ComponentAssignmentEntry entry) {
        if (entry == null) {
            return;
        }
        showPolicyDetailsById(entry.policyId());
    }

    public void showPolicyComponentDetails(PoliciesParser.ComponentAssignmentEntry entry) {
        if (entry == null) {
            return;
        }
        showPolicyDetailsById(entry.policyId());
    }

    public void showUsagePolicyDetails(PoliciesParser.UsagePolicyEntry entry) {
        if (entry == null) {
            return;
        }
        showPolicyDetailsById(entry.policyId());
    }

    private void showPolicyDetailsById(String policyId) {
        if (policyId == null || policyId.isBlank()) {
            return;
        }
        try {
            String policiesPath = getActivePoliciesPath();
            List<PoliciesParser.PolicyEntry> policies = policiesParser.getPolicies(policiesPath);
            for (PoliciesParser.PolicyEntry policy : policies) {
                if (policyId.equalsIgnoreCase(policy.policyId())) {
                    showPolicyDetails(policy);
                    return;
                }
            }
            onLoadError.accept("Policy details not found for: " + policyId);
        } catch (Exception e) {
            reportLoadError("policy details", e);
        }
    }

    private void showDetailWindow(String key, String windowTitle, String headerText, String detailsContent) {
        Stage existing = detailWindows.get(key);
        if (existing != null) {
            existing.toFront();
            existing.requestFocus();
            return;
        }

        String sourceName = activeSourceName();

        TextArea detailsArea = new TextArea(detailsContent);
        detailsArea.getStyleClass().add("code-area");
        detailsArea.setEditable(false);
        detailsArea.setWrapText(false);

        Label title = new Label(headerText);
        title.getStyleClass().add("details-title");
        Label sourceLabel = new Label("Source: " + sourceName);
        sourceLabel.getStyleClass().add("details-source");
        javafx.scene.layout.VBox header = new javafx.scene.layout.VBox(2, title, sourceLabel);
        header.getStyleClass().add("details-header");

        BorderPane root = new BorderPane(detailsArea);
        root.getStyleClass().add("details-popup");
        root.setTop(header);

        Stage popup = new Stage();
        popup.setTitle(sourceName + " \u2014 " + windowTitle);
        Scene scene = new Scene(root, 900, 700);
        ui.applyTheme(scene);
        popup.setScene(scene);
        popup.setOnHidden(event -> detailWindows.remove(key));

        detailWindows.put(key, popup);
        popup.show();
    }

    /** Returns the active source profile's display name, or a sensible fallback when unavailable. */
    private String activeSourceName() {
        AppSettings.SourceProfile profile = settings.getActiveProfile();
        if (profile == null) {
            return "No source";
        }
        String name = profile.displayName();
        return name == null || name.isBlank() ? "Unnamed source" : name;
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
        String rawFolder = profile.folderPath();
        if (rawFolder == null || rawFolder.isBlank()) {
            throw new IllegalStateException("Source profile '" + profile.displayName() + "' has no folder configured.");
        }
        return canonicalizeFolder(rawFolder.trim());
    }

    private Path canonicalizeFolder(String rawFolder) {
        if (rawFolder.indexOf('\u0000') >= 0) {
            throw new IllegalStateException("Folder path contains invalid characters.");
        }
        Path folder;
        try {
            folder = Paths.get(rawFolder);
        } catch (RuntimeException invalidPath) {
            throw new IllegalStateException("Folder path is not valid: " + rawFolder);
        }
        Path normalized = folder.toAbsolutePath().normalize();
        if (Files.exists(normalized) && !Files.isDirectory(normalized)) {
            throw new IllegalStateException("Configured source path is not a folder: " + normalized);
        }
        return normalized;
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
        policiesLoaded = false;
        targetsLoaded = false;
        if (ui.getUsageTable() != null) {
            ui.getUsageTable().setItems(FXCollections.observableArrayList());
        }
        if (ui.getPoliciesTable() != null) {
            ui.getPoliciesTable().setItems(FXCollections.observableArrayList());
        }
        if (ui.getAlteredAddressTable() != null) {
            ui.getAlteredAddressTable().setItems(FXCollections.observableArrayList());
        }
        ui.setPolicyAssignments(List.of());
        ui.setUsagePolicyAssignments(List.of());
        ui.setConnectionAssignments(List.of());
        ui.setTargetDetails(List.of());

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

    private boolean isStale(LocalDateTime loadedAt, FileTime modifiedAtLoad, FileTime currentModified) {
        if (loadedAt == null || modifiedAtLoad == null) {
            return false;
        }
        return currentModified != null && currentModified.compareTo(modifiedAtLoad) > 0;
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

    private String formatLoadStatusLabel(String fileName, LocalDateTime loadedAt, boolean stale) {
        if (loadedAt == null) {
            return fileName + ": never loaded";
        }

        String text = fileName + ": loaded " + LOAD_TIME_FORMATTER.format(loadedAt);
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
        if (UI.TAB_PSMS.equals(selectedTab)) {
            loadPSMServersIfNeeded();
        } else if (UI.TAB_PSMPS.equals(selectedTab)) {
            loadPSMPServersIfNeeded();
        } else if (UI.TAB_POLICIES.equals(selectedTab)) {
            loadPoliciesIfNeeded();
        } else if (UI.TAB_USAGES.equals(selectedTab)) {
            loadUsageIfNeeded();
        } else if (UI.TAB_ALTER_ADDRESSES.equals(selectedTab)) {
            loadTargetsIfNeeded();
        } else {
            loadConnectionComponentIfNeeded();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void wireFiltering(TableView<T> table, ObservableList<T> masterData) {
        FilterBinding binding = filterBindings.get(table);

        if (binding != null && table.getItems() == binding.sorted) {
            ((ObservableList<T>) binding.backing).setAll(masterData);
            return;
        }

        if (binding != null) {
            binding.detachListeners();
        }

        binding = new FilterBinding();
        ObservableList<T> backing = FXCollections.observableArrayList(masterData);
        FilteredList<T> filteredData = new FilteredList<>(backing, p -> true);

        for (TableColumn<T, ?> col : table.getColumns()) {
            if (col.getUserData() instanceof TextField tf) {
                ChangeListener<String> listener = (obs, oldVal, newVal) ->
                        filteredData.setPredicate(entry -> columnFiltersMatch(entry, table));
                tf.textProperty().addListener(listener);
                binding.fields.add(tf);
                binding.listeners.add(listener);
            }
        }

        SortedList<T> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sortedData);

        binding.backing = backing;
        binding.sorted = sortedData;
        filterBindings.put(table, binding);
    }

    private static final class FilterBinding {
        private ObservableList<?> backing;
        private SortedList<?> sorted;
        private final List<TextField> fields = new ArrayList<>();
        private final List<ChangeListener<String>> listeners = new ArrayList<>();

        private void detachListeners() {
            for (int i = 0; i < fields.size() && i < listeners.size(); i++) {
                fields.get(i).textProperty().removeListener(listeners.get(i));
            }
            fields.clear();
            listeners.clear();
        }
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
