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
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
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
    private final PvwaClient pvwaClient = new PvwaClient();
    private PvwaClient.Session pvwaSession;
    private String lastPvwaBaseUri = "https://<pvwa_address>/PasswordVault";
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

    // ------------------------------------------------------------------------------ Order / Import

    private static final String SCOPE_PVCONFIG = "PVCONFIG";
    private static final String SCOPE_POLICY_PREFIX = "POLICY:";

    public void orderConnectionComponents() {
        String policiesPath;
        String pvConfigPath;
        try {
            policiesPath = getActivePoliciesPath();
            pvConfigPath = getActivePvConfigurationPath();
        } catch (Exception e) {
            reportLoadError("order components", e);
            return;
        }

        List<ComponentOperations.OrderScope> scopes = new ArrayList<>();
        Map<String, String> displayNames = new HashMap<>();
        try {
            List<String> definitionIds = new ArrayList<>();
            for (PVConfigurationParser.ConnectionComponentEntry comp : pvParser.GetConnectionComponents(pvConfigPath)) {
                if (comp.id() != null && !comp.id().isBlank()) {
                    definitionIds.add(comp.id());
                    displayNames.put(comp.id(), comp.name() == null ? "" : comp.name());
                }
            }
            if (definitionIds.size() > 1) {
                scopes.add(new ComponentOperations.OrderScope(SCOPE_PVCONFIG,
                        "PVConfiguration.xml \u2014 connection component definitions (sorted by Id)",
                        definitionIds, false));
            }

            for (PoliciesParser.PolicyEntry policy : policiesParser.getPolicies(policiesPath)) {
                String assigned = policy.assignedComponents();
                if (assigned == null || assigned.isBlank()) {
                    continue;
                }
                List<String> ids = new ArrayList<>();
                for (String part : assigned.split(",")) {
                    String id = part.trim();
                    if (!id.isBlank()) {
                        ids.add(id);
                    }
                }
                if (ids.size() > 1) {
                    String platform = policy.platformId() == null || policy.platformId().isBlank()
                            ? "" : " (" + policy.platformId() + ")";
                    scopes.add(new ComponentOperations.OrderScope(SCOPE_POLICY_PREFIX + policy.policyId(),
                            "Policy: " + policy.policyId() + platform + " (sorted by DisplayName)", ids, true));
                }
            }
        } catch (Exception e) {
            reportLoadError("order components", e);
            return;
        }

        if (scopes.isEmpty()) {
            onLoadError.accept("Nothing to order: no multi-component definitions or policies were found.");
            return;
        }

        List<ComponentOperations.OrderScope> result = ui.showOrderComponentsDialog(scopes, displayNames);
        if (result == null) {
            return; // User cancelled.
        }

        List<String> pvDefinitionOrder = null;
        Map<String, List<String>> policyOrders = new LinkedHashMap<>();
        for (ComponentOperations.OrderScope scope : result) {
            if (SCOPE_PVCONFIG.equals(scope.key())) {
                pvDefinitionOrder = scope.componentIds();
            } else if (scope.key() != null && scope.key().startsWith(SCOPE_POLICY_PREFIX)) {
                policyOrders.put(scope.key().substring(SCOPE_POLICY_PREFIX.length()), scope.componentIds());
            }
        }

        Path outputRoot = Paths.get("output").toAbsolutePath().normalize();
        String sourceLabel = activeSourceName();
        String sourceFolder = activeSourceFolder();

        try {
            ComponentOperations.OrderResult result2 = componentOperations.applyComponentOrder(
                    pvConfigPath, policiesPath, pvDefinitionOrder, policyOrders, outputRoot, sourceLabel, sourceFolder);
            onLoadError.accept("Ordered " + result2.pvReordered() + " definition(s) and "
                    + result2.policiesReordered() + " policy block(s). Output written to " + result2.outputFolder());
        } catch (Exception e) {
            reportLoadError("order components", e);
        }
    }

    public void importPsmComponents() {
        String pvConfigPath;
        try {
            pvConfigPath = getActivePvConfigurationPath();
        } catch (Exception e) {
            reportLoadError("import PSM component", e);
            return;
        }

        List<File> chosen = ui.chooseImportFiles();
        if (chosen == null || chosen.isEmpty()) {
            return; // User cancelled.
        }

        List<Path> zips = new ArrayList<>();
        for (File file : chosen) {
            if (file != null) {
                zips.add(file.toPath());
            }
        }
        if (zips.isEmpty()) {
            return;
        }

        Path outputRoot = Paths.get("output").toAbsolutePath().normalize();
        String sourceLabel = activeSourceName();
        String sourceFolder = activeSourceFolder();

        try {
            ComponentOperations.ImportResult result = componentOperations.importConnectionComponents(
                    pvConfigPath, zips, outputRoot, sourceLabel, sourceFolder);

            if (!result.imported()) {
                String message = "No connection components imported.";
                if (!result.skipped().isEmpty()) {
                    message += " Skipped: " + String.join("; ", result.skipped());
                }
                onLoadError.accept(message);
                return;
            }

            String summary = "Imported " + result.importedIds().size() + " connection component(s): "
                    + String.join(", ", result.importedIds()) + ". Output written to " + result.outputFolder();
            if (!result.skipped().isEmpty()) {
                summary += " (skipped " + result.skipped().size() + ")";
            }
            onLoadError.accept(summary);
        } catch (Exception e) {
            reportLoadError("import PSM component", e);
        }
    }

    // ------------------------------------------------------------------------------ Online (PVWA REST)

    public void connectToPvwa() {
        PvwaClient.Credentials credentials = ui.showPvwaLogonDialog(lastPvwaBaseUri);
        if (credentials == null) {
            return; // User cancelled.
        }
        lastPvwaBaseUri = credentials.baseUri();
        onLoadError.accept("Connecting to " + credentials.baseUri() + " ...");

        runAsync("PVWA logon",
                () -> pvwaClient.logon(credentials),
                session -> {
                    pvwaSession = session;
                    ui.setPvwaStatus("PVWA: connected (" + session.baseUri() + ")", true);
                    onLoadError.accept("Connected to PVWA as " + credentials.username() + ".");
                },
                () -> {
                    pvwaSession = null;
                    ui.setPvwaStatus("PVWA: not connected", false);
                });
    }

    public void disconnectFromPvwa() {
        PvwaClient.Session session = pvwaSession;
        if (session == null) {
            onLoadError.accept("Not connected to PVWA.");
            return;
        }
        pvwaSession = null;
        ui.setPvwaStatus("PVWA: not connected", false);
        runAsync("PVWA logoff",
                () -> {
                    pvwaClient.logoff(session);
                    return Boolean.TRUE;
                },
                ignored -> onLoadError.accept("Disconnected from PVWA."),
                null);
    }

    public void importPsmComponentsOnline() {
        if (!requirePvwaSession()) {
            return;
        }

        List<File> chosen = ui.chooseImportFiles();
        if (chosen == null || chosen.isEmpty()) {
            return; // User cancelled.
        }

        List<Path> zips = new ArrayList<>();
        for (File file : chosen) {
            if (file != null) {
                zips.add(file.toPath());
            }
        }
        if (zips.isEmpty()) {
            return;
        }

        PvwaClient.Session session = pvwaSession;
        onLoadError.accept("Importing " + zips.size() + " package(s) to PVWA ...");

        runAsync("online import",
                () -> {
                    OnlineImportSummary summary = new OnlineImportSummary();
                    for (Path zip : zips) {
                        String label = zip.getFileName() == null ? zip.toString() : zip.getFileName().toString();
                        try {
                            byte[] bytes = Files.readAllBytes(zip);
                            pvwaClient.importConnectionComponent(session, bytes);
                            summary.succeeded.add(label);
                        } catch (Exception e) {
                            summary.failed.add(label + " (" + messageOf(e) + ")");
                        }
                    }
                    return summary;
                },
                this::reportOnlineImport,
                null);
    }

    public void importSelectedComponentsOnline(List<PVConfigurationParser.ConnectionComponentEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            onLoadError.accept("Select at least one connection component first.");
            return;
        }
        if (!requirePvwaSession()) {
            return;
        }

        String pvConfigPath;
        try {
            pvConfigPath = getActivePvConfigurationPath();
        } catch (Exception e) {
            reportLoadError("online import", e);
            return;
        }

        List<String> ids = collectComponentIds(entries);
        if (ids.isEmpty()) {
            onLoadError.accept("Select at least one connection component first.");
            return;
        }

        PvwaClient.Session session = pvwaSession;
        if (!ui.confirm("Import to PVWA",
                "Import " + ids.size() + " connection component(s) to " + session.baseUri() + "?\n\n"
                        + String.join(", ", ids))) {
            return;
        }

        onLoadError.accept("Importing " + ids.size() + " component(s) to PVWA ...");

        runAsync("online import",
                () -> {
                    OnlineImportSummary summary = new OnlineImportSummary();
                    for (String id : ids) {
                        try {
                            byte[] bytes = componentOperations.packageConnectionComponent(pvConfigPath, id);
                            pvwaClient.importConnectionComponent(session, bytes);
                            summary.succeeded.add(id);
                        } catch (Exception e) {
                            summary.failed.add(id + " (" + messageOf(e) + ")");
                        }
                    }
                    return summary;
                },
                this::reportOnlineImport,
                null);
    }

    private boolean requirePvwaSession() {
        if (pvwaSession == null) {
            onLoadError.accept("Connect to PVWA first (PVWA menu \u2192 Connect to PVWA).");
            return false;
        }
        return true;
    }

    private void reportOnlineImport(OnlineImportSummary summary) {
        if (summary.failed.isEmpty()) {
            onLoadError.accept("Imported " + summary.succeeded.size() + " connection component(s) to PVWA: "
                    + String.join(", ", summary.succeeded));
        } else if (summary.succeeded.isEmpty()) {
            onLoadError.accept("PVWA import failed: " + String.join("; ", summary.failed));
        } else {
            onLoadError.accept("Imported " + summary.succeeded.size() + " to PVWA ("
                    + String.join(", ", summary.succeeded) + "); failed " + summary.failed.size() + ": "
                    + String.join("; ", summary.failed));
        }
    }

    private static String messageOf(Exception e) {
        return (e == null || e.getMessage() == null || e.getMessage().isBlank())
                ? (e == null ? "unknown error" : e.getClass().getSimpleName())
                : e.getMessage();
    }

    private static final class OnlineImportSummary {
        private final List<String> succeeded = new ArrayList<>();
        private final List<String> failed = new ArrayList<>();
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

    // ---------------------------------------------------------------------------------------- compare

    public void loadCompareItems(String sourceId, Compare.Kind kind,
                                 Consumer<List<Compare.Item>> onLoaded, Consumer<String> onError) {
        AppSettings.SourceProfile profile = findProfile(sourceId);
        if (profile == null || kind == null) {
            if (onError != null) {
                onError.accept("Pick a source first.");
            }
            return;
        }
        String pvPath;
        String policiesPath;
        try {
            Path folder = folderFor(profile);
            pvPath = folder.resolve(PV_CONFIGURATION_FILE).toString();
            policiesPath = folder.resolve(POLICIES_FILE).toString();
        } catch (Exception error) {
            if (onError != null) {
                onError.accept(messageOf(error));
            }
            return;
        }
        submitBackground(() -> {
            try {
                List<Compare.Item> items = buildCompareItems(kind, pvPath, policiesPath);
                Platform.runLater(() -> onLoaded.accept(items));
            } catch (Exception error) {
                Platform.runLater(() -> {
                    if (onError != null) {
                        onError.accept(messageOf(error));
                    }
                });
            }
        });
    }

    private List<Compare.Item> buildCompareItems(Compare.Kind kind, String pvPath, String policiesPath) throws Exception {
        Map<String, Compare.Item> byId = new LinkedHashMap<>();
        switch (kind) {
            case CONNECTION_COMPONENT -> {
                for (PVConfigurationParser.ConnectionComponentEntry component : pvParser.GetConnectionComponents(pvPath)) {
                    String label = component.name() == null || component.name().isBlank()
                            ? component.id()
                            : component.id() + "  (" + component.name() + ")";
                    byId.putIfAbsent(component.id(), new Compare.Item(component.id(), label));
                }
            }
            case USAGE -> {
                for (PoliciesParser.usageEntry usage : policiesParser.getUsage(policiesPath)) {
                    byId.putIfAbsent(usage.usageId(), new Compare.Item(usage.usageId(), usage.usageId()));
                }
            }
            case POLICY -> {
                for (PoliciesParser.PolicyEntry policy : policiesParser.getPolicies(policiesPath)) {
                    byId.putIfAbsent(policy.policyId(), new Compare.Item(policy.policyId(), policy.policyId()));
                }
            }
        }
        List<Compare.Item> items = new ArrayList<>(byId.values());
        items.sort(Comparator.comparing(item -> item.id() == null ? "" : item.id().toLowerCase()));
        return items;
    }

    public void runCompare(Compare.Kind kind, String sourceAId, Compare.Item itemA, String sourceBId, Compare.Item itemB) {
        AppSettings.SourceProfile profileA = findProfile(sourceAId);
        AppSettings.SourceProfile profileB = findProfile(sourceBId);
        if (kind == null || profileA == null || profileB == null || itemA == null || itemB == null) {
            onLoadError.accept("Pick an item on both sides to compare.");
            return;
        }
        String pvA;
        String policiesA;
        String pvB;
        String policiesB;
        try {
            Path folderA = folderFor(profileA);
            Path folderB = folderFor(profileB);
            pvA = folderA.resolve(PV_CONFIGURATION_FILE).toString();
            policiesA = folderA.resolve(POLICIES_FILE).toString();
            pvB = folderB.resolve(PV_CONFIGURATION_FILE).toString();
            policiesB = folderB.resolve(POLICIES_FILE).toString();
        } catch (Exception error) {
            onLoadError.accept(messageOf(error));
            return;
        }
        runAsync("comparison",
                () -> buildCompareResult(kind, profileA, profileB, itemA, itemB, pvA, policiesA, pvB, policiesB),
                ui::showCompareResult,
                null);
    }

    private Compare.Result buildCompareResult(Compare.Kind kind,
                                              AppSettings.SourceProfile profileA, AppSettings.SourceProfile profileB,
                                              Compare.Item itemA, Compare.Item itemB,
                                              String pvA, String policiesA, String pvB, String policiesB) throws Exception {
        PVConfigurationParser.XmlNode nodeA = loadCompareNode(kind, itemA.id(), pvA, policiesA);
        PVConfigurationParser.XmlNode nodeB = loadCompareNode(kind, itemB.id(), pvB, policiesB);
        if (nodeA == null || nodeB == null) {
            throw new IllegalStateException("Could not find one of the selected items in its source.");
        }
        List<Compare.Row> rows = Compare.diff(Compare.flatten(nodeA), Compare.flatten(nodeB));
        return new Compare.Result(
                kind.label() + " comparison",
                displaySource(profileA) + "  \u2022  " + itemA.id(),
                displaySource(profileB) + "  \u2022  " + itemB.id(),
                rows,
                Compare.countDifferences(rows));
    }

    private PVConfigurationParser.XmlNode loadCompareNode(Compare.Kind kind, String id, String pvPath, String policiesPath) throws Exception {
        switch (kind) {
            case CONNECTION_COMPONENT -> {
                for (PVConfigurationParser.ConnectionComponentEntry component : pvParser.GetConnectionComponents(pvPath)) {
                    if (idEquals(component.id(), id)) {
                        return component.details();
                    }
                }
            }
            case USAGE -> {
                for (PoliciesParser.usageEntry usage : policiesParser.getUsage(policiesPath)) {
                    if (idEquals(usage.usageId(), id)) {
                        if (usage.children() != null && !usage.children().isEmpty()) {
                            return usage.children().get(0);
                        }
                        return syntheticUsageNode(usage);
                    }
                }
            }
            case POLICY -> {
                for (PoliciesParser.PolicyEntry policy : policiesParser.getPolicies(policiesPath)) {
                    if (idEquals(policy.policyId(), id)) {
                        return policy.details();
                    }
                }
            }
        }
        return null;
    }

    private PVConfigurationParser.XmlNode syntheticUsageNode(PoliciesParser.usageEntry usage) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("ID", usage.usageId());
        if (usage.platformBaseId() != null && !usage.platformBaseId().isBlank()) {
            attributes.put("PlatformBaseID", usage.platformBaseId());
        }
        if (usage.platformBaseProtocol() != null && !usage.platformBaseProtocol().isBlank()) {
            attributes.put("PlatformBaseProtocol", usage.platformBaseProtocol());
        }
        if (usage.platformBaseType() != null && !usage.platformBaseType().isBlank()) {
            attributes.put("PlatformBaseType", usage.platformBaseType());
        }
        return new Parser.XmlNode("Usage", attributes, List.of());
    }

    private AppSettings.SourceProfile findProfile(String sourceId) {
        if (sourceId == null) {
            return null;
        }
        for (AppSettings.SourceProfile profile : settings.getSourceProfiles()) {
            if (sourceId.equals(profile.id())) {
                return profile;
            }
        }
        return null;
    }

    private Path folderFor(AppSettings.SourceProfile profile) {
        String rawFolder = profile.folderPath();
        if (rawFolder == null || rawFolder.isBlank()) {
            throw new IllegalStateException("Source '" + displaySource(profile) + "' has no folder configured.");
        }
        return canonicalizeFolder(rawFolder.trim());
    }

    private static boolean idEquals(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

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