import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class UI {
    static final String TAB_CONNECTION_COMPONENTS = "Connection Components";
    static final String TAB_POLICIES = "Policies";
    static final String TAB_USAGES = "Usages";
    static final String TAB_ALTER_ADDRESSES = "Alter Addresses";
    static final String TAB_PSMS = "PSMs";
    static final String TAB_PSMPS = "PSMPs";

    // Application metadata used by the About window and the main stage title.
    private static final String APP_NAME = "CyberArk VaultOps";
    private static final String APP_VERSION = "1.0";
    private static final String GITHUB_URL = "https://github.com/SzSz-hub/CyberArk-VaultOps";

    private final AppSettings settings;
    private final AppSettingsStore settingsStore;
    private final ThemeManager themeManager;
    private final List<ThemeManager.ThemeOption> availableThemes = new ArrayList<>();

    private BorderPane root;
    private VBox toastContainer;
    private SideNav sideNav;
    private Stage primaryStage;
    private Scene mainScene;
    private Menu themeMenu;
    private boolean suppressSideNavCallbacks;
    private Stage aboutStage;

    private static final Runnable NO_OP = () -> {};
    private static final Duration STATUS_POLL_INTERVAL = Duration.seconds(20);
    private Runnable onPsmTabSelected = NO_OP;
    private Runnable onSourceProfileChanged = NO_OP;
    private Runnable onPsmpTabSelected = NO_OP;
    private Runnable onConnectionComponentTabSelected = NO_OP;
    private Runnable onPoliciesTabSelected = NO_OP;
    private Runnable onUsagesTabSelected = NO_OP;
    private Runnable onTargetsTabSelected = NO_OP;
    private Runnable onRefreshCurrentRequested = NO_OP;
    private Runnable onReloadAllRequested = NO_OP;
    private Runnable onStatusRefreshRequested = NO_OP;
    private Runnable onAppClose = NO_OP;

    private static Runnable safeRunnable(Runnable runnable) {
        return runnable == null ? NO_OP : runnable;
    }

    private TableView<PVConfigurationParser.PSMServerEntry> psmTable;
    private VBox psmContent;

    UI(AppSettings settings, AppSettingsStore settingsStore) {
        this.settings = settings;
        this.settingsStore = settingsStore;
        this.themeManager = new ThemeManager(settingsStore.getThemesDirectory());
    }

    void setOnSourceProfileChanged(Runnable onSourceProfileChanged) {
        this.onSourceProfileChanged = safeRunnable(onSourceProfileChanged);
    }

    TableView<PVConfigurationParser.PSMServerEntry> getPsmTable() {
        return psmTable;
    }

    void setOnPsmTabSelected(Runnable onPsmTabSelected) {
        this.onPsmTabSelected = safeRunnable(onPsmTabSelected);
    }

    private TableView<PVConfigurationParser.PSMPServerEntry> psmpTable;
    private VBox psmpContent;

    TableView<PVConfigurationParser.PSMPServerEntry> getPsmpTable() {
        return psmpTable;
    }

    void setOnPsmpTabSelected(Runnable onPsmpTabSelected) {
        this.onPsmpTabSelected = safeRunnable(onPsmpTabSelected);
    }

    private TableView<PoliciesParser.usageEntry> usageTable;
    private VBox usageContent;
    private TableView<PoliciesParser.UsagePolicyEntry> usagePolicyAssignmentsTable;

    TableView<PoliciesParser.usageEntry> getUsageTable() {
        return usageTable;
    }

    private TableView<PVConfigurationParser.ConnectionComponentEntry> connectionComponentTable;
    private VBox connectionComponentContent;

    private java.util.function.Consumer<PVConfigurationParser.ConnectionComponentEntry> onConnectionComponentRowDoubleClicked = entry -> {
    };
    private java.util.function.Consumer<PVConfigurationParser.ConnectionComponentEntry> onConnectionComponentSelected = entry -> {
    };
    private java.util.function.Consumer<List<PVConfigurationParser.ConnectionComponentEntry>> onConnectionComponentExport = entries -> {
    };
    private java.util.function.Consumer<List<PVConfigurationParser.ConnectionComponentEntry>> onConnectionComponentRemove = entries -> {
    };
    private java.util.function.Consumer<List<PVConfigurationParser.ConnectionComponentEntry>> onConnectionComponentUnlink = entries -> {
    };
    private java.util.function.Consumer<PoliciesParser.PolicyEntry> onPolicyRowSelected = entry -> {
    };
    private java.util.function.Consumer<PoliciesParser.PolicyEntry> onPolicyRowDoubleClicked = entry -> {
    };
    private java.util.function.Consumer<PoliciesParser.usageEntry> onUsageRowSelected = entry -> {
    };
    private java.util.function.Consumer<PoliciesParser.usageEntry> onUsageRowDoubleClicked = entry -> {
    };
    private java.util.function.Consumer<PoliciesParser.ComponentAssignmentEntry> onConnectionAssignmentDoubleClicked = entry -> {
    };
    private java.util.function.Consumer<PoliciesParser.ComponentAssignmentEntry> onPolicyComponentDoubleClicked = entry -> {
    };
    private java.util.function.Consumer<PoliciesParser.UsagePolicyEntry> onUsagePolicyDoubleClicked = entry -> {
    };

    TableView<PVConfigurationParser.ConnectionComponentEntry> getConnectionComponentTable() {
        return connectionComponentTable;
    }

    void setOnConnectionComponentTabSelected(Runnable onConnectionComponentTabSelected) {
        this.onConnectionComponentTabSelected = safeRunnable(onConnectionComponentTabSelected);
    }

    void setOnPoliciesTabSelected(Runnable onPoliciesTabSelected) {
        this.onPoliciesTabSelected = safeRunnable(onPoliciesTabSelected);
    }

    void setOnUsagesTabSelected(Runnable onUsagesTabSelected) {
        this.onUsagesTabSelected = safeRunnable(onUsagesTabSelected);
    }

    void setOnTargetsTabSelected(Runnable onTargetsTabSelected) {
        this.onTargetsTabSelected = safeRunnable(onTargetsTabSelected);
    }

    void setOnConnectionComponentSelected(java.util.function.Consumer<PVConfigurationParser.ConnectionComponentEntry> onConnectionComponentSelected) {
        this.onConnectionComponentSelected = onConnectionComponentSelected == null ? entry -> {
        } : onConnectionComponentSelected;
    }

    void setOnConnectionComponentExport(java.util.function.Consumer<List<PVConfigurationParser.ConnectionComponentEntry>> onConnectionComponentExport) {
        this.onConnectionComponentExport = onConnectionComponentExport == null ? entries -> {
        } : onConnectionComponentExport;
    }

    void setOnConnectionComponentRemove(java.util.function.Consumer<List<PVConfigurationParser.ConnectionComponentEntry>> onConnectionComponentRemove) {
        this.onConnectionComponentRemove = onConnectionComponentRemove == null ? entries -> {
        } : onConnectionComponentRemove;
    }

    void setOnConnectionComponentUnlink(java.util.function.Consumer<List<PVConfigurationParser.ConnectionComponentEntry>> onConnectionComponentUnlink) {
        this.onConnectionComponentUnlink = onConnectionComponentUnlink == null ? entries -> {
        } : onConnectionComponentUnlink;
    }

    void setOnPolicyRowSelected(java.util.function.Consumer<PoliciesParser.PolicyEntry> onPolicyRowSelected) {
        this.onPolicyRowSelected = onPolicyRowSelected == null ? entry -> {
        } : onPolicyRowSelected;
    }

    void setOnPolicyRowDoubleClicked(java.util.function.Consumer<PoliciesParser.PolicyEntry> onPolicyRowDoubleClicked) {
        this.onPolicyRowDoubleClicked = onPolicyRowDoubleClicked == null ? entry -> {
        } : onPolicyRowDoubleClicked;
    }

    void setOnUsageRowSelected(java.util.function.Consumer<PoliciesParser.usageEntry> onUsageRowSelected) {
        this.onUsageRowSelected = onUsageRowSelected == null ? entry -> {
        } : onUsageRowSelected;
    }

    void setOnUsageRowDoubleClicked(java.util.function.Consumer<PoliciesParser.usageEntry> onUsageRowDoubleClicked) {
        this.onUsageRowDoubleClicked = onUsageRowDoubleClicked == null ? entry -> {
        } : onUsageRowDoubleClicked;
    }

    void setOnConnectionComponentRowDoubleClicked(java.util.function.Consumer<PVConfigurationParser.ConnectionComponentEntry> onConnectionComponentRowDoubleClicked) {
        this.onConnectionComponentRowDoubleClicked = onConnectionComponentRowDoubleClicked == null ? entry -> {
        } : onConnectionComponentRowDoubleClicked;
    }

    void setOnConnectionAssignmentDoubleClicked(java.util.function.Consumer<PoliciesParser.ComponentAssignmentEntry> onConnectionAssignmentDoubleClicked) {
        this.onConnectionAssignmentDoubleClicked = onConnectionAssignmentDoubleClicked == null ? entry -> {
        } : onConnectionAssignmentDoubleClicked;
    }

    void setOnPolicyComponentDoubleClicked(java.util.function.Consumer<PoliciesParser.ComponentAssignmentEntry> onPolicyComponentDoubleClicked) {
        this.onPolicyComponentDoubleClicked = onPolicyComponentDoubleClicked == null ? entry -> {
        } : onPolicyComponentDoubleClicked;
    }

    void setOnUsagePolicyDoubleClicked(java.util.function.Consumer<PoliciesParser.UsagePolicyEntry> onUsagePolicyDoubleClicked) {
        this.onUsagePolicyDoubleClicked = onUsagePolicyDoubleClicked == null ? entry -> {
        } : onUsagePolicyDoubleClicked;
    }

    TableView<PoliciesParser.PolicyEntry> getPoliciesTable() {
        return policiesTable;
    }

    TableView<PoliciesParser.AlteredAddressEntry> getAlteredAddressTable() {
        return alterAddressTable;
    }

    void setOnAlteredAddressSelected(java.util.function.Consumer<String> onAlteredAddressSelected) {
        this.onAlteredAddressSelected = onAlteredAddressSelected == null ? address -> {
        } : onAlteredAddressSelected;
    }

    void setOnRefreshCurrentRequested(Runnable onRefreshCurrentRequested) {
        this.onRefreshCurrentRequested = safeRunnable(onRefreshCurrentRequested);
    }

    void setOnReloadAllRequested(Runnable onReloadAllRequested) {
        this.onReloadAllRequested = safeRunnable(onReloadAllRequested);
    }

    void setOnStatusRefreshRequested(Runnable onStatusRefreshRequested) {
        this.onStatusRefreshRequested = safeRunnable(onStatusRefreshRequested);
    }

    void setOnAppClose(Runnable onAppClose) {
        this.onAppClose = safeRunnable(onAppClose);
    }

    private TabPane tabPane;
    private Timeline statusPoller;
    private Label sourceStatusLabel;
    private Label pvLoadStatusLabel;
    private Label policiesLoadStatusLabel;
    private TableView<PoliciesParser.PolicyEntry> policiesTable;
    private VBox policiesContent;
    private TableView<PoliciesParser.ComponentAssignmentEntry> connectionAssignmentTable;
    private VBox targetsContent;
    private TableView<PoliciesParser.AlteredAddressEntry> alterAddressTable;
    private TableView<PoliciesParser.TargetDetailEntry> targetDetailsTable;
    private java.util.function.Consumer<String> onAlteredAddressSelected = address -> {
    };
    private TableView<PoliciesParser.ComponentAssignmentEntry> policyAssignmentsTable;

    void setupUI(Stage stage) {
        this.primaryStage = stage;
        refreshAvailableThemes();
        root = new BorderPane();
        root.getStyleClass().add("app-body");
        root.setTop(createTopBar());
        sideNav = new SideNav();
        sideNav.setOnProfileSelected(profile -> {
            if (suppressSideNavCallbacks) {
                return;
            }
            settings.setActiveProfileId(profile.id());
            settingsStore.save(settings);
            onSourceProfileChanged.run();
            reloadCurrentTab();
        });
        sideNav.setOnProfilesReordered(reordered -> {
            if (suppressSideNavCallbacks) {
                return;
            }
            settings.replaceSourceProfiles(reordered);
            settingsStore.save(settings);
            applyProfilesToSidebar();
        });
        root.setLeft(sideNav);
        root.setCenter(getPsmContent());
        root.setBottom(createStatusBar());

        applyProfilesToSidebar();

        tabPane.getSelectionModel().select(0);

        stage.setTitle(APP_NAME + " v" + APP_VERSION);
        try {
            stage.getIcons().setAll(AppIcon.createIcons());
        } catch (RuntimeException iconError) {
            // Icon rendering must never block application startup; fall back to the default icon.
            System.err.println("Could not generate application icon: " + iconError.getMessage());
        }
        StackPane sceneRoot = new StackPane(root);
        sceneRoot.getStyleClass().add("app-shell");
        toastContainer = new VBox(8);
        toastContainer.getStyleClass().add("toast-container");
        toastContainer.setMouseTransparent(true);
        StackPane.setAlignment(toastContainer, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(toastContainer, new Insets(14));
        sceneRoot.getChildren().add(toastContainer);

        mainScene = new Scene(sceneRoot, 900, 600);
        applyTheme(mainScene);
        stage.setScene(mainScene);
        stage.setOnCloseRequest(event -> {
            stopStatusPolling();
            onAppClose.run();
        });
        stage.show();
        startStatusPolling();
    }

    private VBox createTopBar() {
        return new VBox(createMenuBar(), createTabPane());
    }

    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();
        menuBar.getStyleClass().add("app-menu-bar");

        Menu fileMenu = new Menu("File");
        MenuItem exportComponentsItem = new MenuItem("Export Selected Components...");
        exportComponentsItem.setOnAction(event -> exportSelectedComponentsFromMenu());
        fileMenu.getItems().addAll(
                new MenuItem("Open XML file"),
                new MenuItem("Import PSM Component"),
                exportComponentsItem,
                new MenuItem("Export to CSV"),
                new MenuItem("Exit")
        );

        Menu editMenu = new Menu("Edit");
        MenuItem removeComponentsItem = new MenuItem("Remove Selected Components...");
        removeComponentsItem.setOnAction(event -> removeSelectedComponentsFromMenu());
        MenuItem unlinkComponentsItem = new MenuItem("Unlink Selected from Policies...");
        unlinkComponentsItem.setOnAction(event -> unlinkSelectedComponentsFromMenu());
        editMenu.getItems().addAll(
                new MenuItem("Order"),
                removeComponentsItem,
                unlinkComponentsItem
        );

        Menu settingsMenu = new Menu("Settings");
        MenuItem sourcesItem = new MenuItem("Sources");
        sourcesItem.setOnAction(event -> openSourcesSettingsDialog());
        themeMenu = new Menu("Theme");
        refreshThemeMenu();
        settingsMenu.getItems().addAll(sourcesItem, themeMenu);

        Menu viewMenu = new Menu("View");

        Menu helpMenu = new Menu("Help");
        MenuItem aboutItem = new MenuItem("About " + APP_NAME);
        aboutItem.setOnAction(event -> openAboutDialog());
        helpMenu.getItems().add(aboutItem);

        menuBar.getMenus().addAll(fileMenu, editMenu, settingsMenu, viewMenu, helpMenu);
        return menuBar;
    }

    private TabPane createTabPane() {
        tabPane = new TabPane();
        tabPane.getStyleClass().add("main-tab-pane");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab connectionComponents = new Tab(TAB_CONNECTION_COMPONENTS);
        Tab policies = new Tab(TAB_POLICIES);
        Tab usages = new Tab(TAB_USAGES);
        Tab targets = new Tab(TAB_ALTER_ADDRESSES);
        Tab psms = new Tab(TAB_PSMS);
        Tab psmps = new Tab(TAB_PSMPS);

        tabPane.getTabs().addAll(connectionComponents, policies, usages, targets, psms, psmps);

        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (root == null) return;
            if (newTab == connectionComponents) {
                root.setCenter(getConnectionComponentContent());
                onConnectionComponentTabSelected.run();
            } else if (newTab == policies) {
                root.setCenter(getPoliciesContent());
                onPoliciesTabSelected.run();
            } else if (newTab == usages) {
                root.setCenter(getUsagesContent());
                onUsagesTabSelected.run();
            } else if (newTab == targets) {
                root.setCenter(getTargetsContent());
                onTargetsTabSelected.run();
            }
            else if (newTab == psms) {
                root.setCenter(getPsmContent());
                onPsmTabSelected.run();
            } else if (newTab == psmps) {
                root.setCenter(getPsmpContent());
                onPsmpTabSelected.run();
            }
        });

        return tabPane;
    }

    // --- Tab content ---

    private VBox getConnectionComponentContent() {
        if (connectionComponentContent == null) {
            connectionComponentContent = new VBox();
            connectionComponentContent.getStyleClass().add("content-pane");

            connectionComponentTable = new TableView<>();
            connectionComponentTable.getStyleClass().add("modern-table");
            connectionComponentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
            connectionComponentTable.setPlaceholder(new Label("No Connection Component loaded"));
            connectionComponentTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            connectionComponentTable.getColumns().addAll(
                    makeColumn("ID", PVConfigurationParser.ConnectionComponentEntry::id, 120),
                    makeColumn("Name", PVConfigurationParser.ConnectionComponentEntry::name, 220),
                    makeColumn("Client App", PVConfigurationParser.ConnectionComponentEntry::ClientApp, 160),
                    makeColumn("Client Dispatcher", PVConfigurationParser.ConnectionComponentEntry::ClientDispatcher, 160),
                    makeIntegerColumn("Assignment Count", PVConfigurationParser.ConnectionComponentEntry::assignmentCount, 120)
            );

            ContextMenu connectionComponentMenu = buildConnectionComponentContextMenu();

            connectionComponentTable.setRowFactory(tv -> {
                TableRow<PVConfigurationParser.ConnectionComponentEntry> row = new TableRow<>();
                // Right-clicking an unselected row selects just that row before the menu opens.
                row.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                    if (event.getButton() == MouseButton.SECONDARY && !row.isEmpty()
                            && !connectionComponentTable.getSelectionModel().getSelectedItems().contains(row.getItem())) {
                        connectionComponentTable.getSelectionModel().clearAndSelect(row.getIndex());
                    }
                });
                row.setOnMouseClicked(event -> {
                    if (row.isEmpty()) {
                        return;
                    }
                    if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) {
                        onConnectionComponentRowDoubleClicked.accept(row.getItem());
                    }
                });
                row.contextMenuProperty().bind(
                        javafx.beans.binding.Bindings.when(row.emptyProperty())
                                .then((ContextMenu) null)
                                .otherwise(connectionComponentMenu));
                return row;
            });

            connectionComponentTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    onConnectionComponentSelected.accept(newVal);
                }
            });

            connectionAssignmentTable = new TableView<>();
            connectionAssignmentTable.getStyleClass().add("modern-table");
            connectionAssignmentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
            connectionAssignmentTable.setPlaceholder(new Label("Select a component to see assigned platforms"));
            connectionAssignmentTable.getColumns().addAll(
                    makeColumn("Policy", PoliciesParser.ComponentAssignmentEntry::policyId, 120),
                    makeColumn("Component", PoliciesParser.ComponentAssignmentEntry::componentId, 160),
                    makeColumn("Component Enabled", PoliciesParser.ComponentAssignmentEntry::componentEnabled, 110),
                    makeColumn("Overwrites", PoliciesParser.ComponentAssignmentEntry::hasOverrides, 90)
            );

            // Add double-click handler for connection assignments
            connectionAssignmentTable.setRowFactory(tv -> {
                TableRow<PoliciesParser.ComponentAssignmentEntry> row = new TableRow<>();
                row.setOnMouseClicked(event -> {
                    if (row.isEmpty() || event.getClickCount() != 2 || event.getButton() != MouseButton.PRIMARY) {
                        return;
                    }
                    onConnectionAssignmentDoubleClicked.accept(row.getItem());
                });
                return row;
            });

            VBox.setVgrow(connectionComponentTable, Priority.ALWAYS);
            VBox.setVgrow(connectionAssignmentTable, Priority.ALWAYS);

            SplitPane split = new SplitPane(connectionComponentTable, connectionAssignmentTable);
            split.setDividerPositions(0.62);
            VBox.setVgrow(split, Priority.ALWAYS);
            connectionComponentContent.getChildren().add(split);

        }
        return connectionComponentContent;
    }

    private ContextMenu buildConnectionComponentContextMenu() {
        ContextMenu menu = new ContextMenu();

        MenuItem exportItem = new MenuItem("Export...");
        exportItem.setOnAction(event -> {
            List<PVConfigurationParser.ConnectionComponentEntry> selection = getSelectedConnectionComponents();
            if (!selection.isEmpty()) {
                onConnectionComponentExport.accept(selection);
            }
        });

        MenuItem removeItem = new MenuItem("Remove component...");
        removeItem.setOnAction(event -> {
            List<PVConfigurationParser.ConnectionComponentEntry> selection = getSelectedConnectionComponents();
            if (!selection.isEmpty()) {
                onConnectionComponentRemove.accept(selection);
            }
        });

        MenuItem unlinkItem = new MenuItem("Unlink from policies...");
        unlinkItem.setOnAction(event -> {
            List<PVConfigurationParser.ConnectionComponentEntry> selection = getSelectedConnectionComponents();
            if (!selection.isEmpty()) {
                onConnectionComponentUnlink.accept(selection);
            }
        });

        menu.getItems().addAll(exportItem, removeItem, unlinkItem);
        return menu;
    }

    private List<PVConfigurationParser.ConnectionComponentEntry> getSelectedConnectionComponents() {
        if (connectionComponentTable == null) {
            return List.of();
        }
        return new ArrayList<>(connectionComponentTable.getSelectionModel().getSelectedItems());
    }

    private void exportSelectedComponentsFromMenu() {
        if (!ensureConnectionComponentSelection()) {
            return;
        }
        onConnectionComponentExport.accept(getSelectedConnectionComponents());
    }

    private void removeSelectedComponentsFromMenu() {
        if (!ensureConnectionComponentSelection()) {
            return;
        }
        onConnectionComponentRemove.accept(getSelectedConnectionComponents());
    }

    private void unlinkSelectedComponentsFromMenu() {
        if (!ensureConnectionComponentSelection()) {
            return;
        }
        onConnectionComponentUnlink.accept(getSelectedConnectionComponents());
    }

    private boolean ensureConnectionComponentSelection() {
        if (connectionComponentTable == null) {
            showToast("Open the Connection Components tab and select one or more components first.");
            return false;
        }
        if (connectionComponentTable.getSelectionModel().getSelectedItems().isEmpty()) {
            showToast("Select one or more connection components first.");
            return false;
        }
        return true;
    }

    File chooseDirectory(String title, File initialDirectory) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title == null ? "Choose folder" : title);
        if (initialDirectory != null && initialDirectory.isDirectory()) {
            chooser.setInitialDirectory(initialDirectory);
        }
        return chooser.showDialog(primaryStage);
    }

    boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle(title);
        alert.setHeaderText(null);
        if (primaryStage != null) {
            alert.initOwner(primaryStage);
            applyTheme(alert.getDialogPane().getScene());
        }
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    ComponentOperations.EmptyPolicyChoice showEmptyPolicyDialog(String policyId, List<String> availableComponentIds) {
        Stage dialog = new Stage();
        dialog.initOwner(primaryStage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Policy would have no connection component");

        Label header = new Label("Policy \"" + policyId + "\" would be left without any connection component.");
        header.getStyleClass().add("details-title");
        header.setWrapText(true);

        Label info = new Label("CyberArk requires at least one connection component per policy. "
                + "Choose a replacement to add, or cancel the whole removal.");
        info.setWrapText(true);

        ComboBox<String> componentBox = new ComboBox<>(FXCollections.observableArrayList(
                availableComponentIds == null ? List.of() : availableComponentIds));
        componentBox.setMaxWidth(Double.MAX_VALUE);
        if (!componentBox.getItems().isEmpty()) {
            int rdpIndex = componentBox.getItems().indexOf("PSM-RDP");
            componentBox.getSelectionModel().select(rdpIndex >= 0 ? rdpIndex : 0);
        }

        CheckBox enabledBox = new CheckBox("Add as enabled (visible)");
        enabledBox.setSelected(true);

        CheckBox applyAllBox = new CheckBox("Apply this choice to all further empty policies");

        final ComponentOperations.EmptyPolicyChoice[] result = {ComponentOperations.EmptyPolicyChoice.cancel()};

        Button addButton = new Button("Add Component");
        addButton.setDefaultButton(true);
        addButton.disableProperty().bind(componentBox.getSelectionModel().selectedItemProperty().isNull());
        addButton.setOnAction(event -> {
            result[0] = ComponentOperations.EmptyPolicyChoice.add(
                    componentBox.getSelectionModel().getSelectedItem(),
                    enabledBox.isSelected(),
                    applyAllBox.isSelected());
            dialog.close();
        });

        Button cancelButton = new Button("Cancel Removal");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(event -> {
            result[0] = ComponentOperations.EmptyPolicyChoice.cancel();
            dialog.close();
        });

        HBox actions = new HBox(8, addButton, cancelButton);
        actions.getStyleClass().add("dialog-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(10,
                header,
                info,
                new Label("Connection component to add:"), componentBox,
                enabledBox,
                applyAllBox,
                actions);
        content.getStyleClass().add("content-pane");
        content.setPadding(new Insets(16));

        Scene scene = new Scene(content, 460, 320);
        applyTheme(scene);
        dialog.setScene(scene);
        dialog.showAndWait();
        return result[0];
    }

    private VBox getPoliciesContent() {
        if (policiesContent == null) {
            policiesContent = new VBox(6);
            policiesContent.getStyleClass().add("content-pane");

            policiesTable = new TableView<>();
            policiesTable.getStyleClass().add("modern-table");
            policiesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
            policiesTable.setPlaceholder(new Label("No policies loaded"));
            policiesTable.getColumns().addAll(
                    makeColumn("PlatformBaseID", PoliciesParser.PolicyEntry::platformId, 130),
                    makeColumn("Policy ID", PoliciesParser.PolicyEntry::policyId, 130),
                    makeColumn("Policy Name", PoliciesParser.PolicyEntry::policyName, 170),
                    makeColumn("Component Assigned", PoliciesParser.PolicyEntry::componentAssigned, 110),
                    makeColumn("Overwrites", PoliciesParser.PolicyEntry::hasOverrides, 90)
            );

            policiesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    onPolicyRowSelected.accept(newVal);
                }
            });

            policiesTable.setRowFactory(tv -> {
                TableRow<PoliciesParser.PolicyEntry> row = new TableRow<>();
                row.setOnMouseClicked(event -> {
                    if (!row.isEmpty() && event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) {
                        onPolicyRowDoubleClicked.accept(row.getItem());
                    }
                });
                return row;
            });

            policyAssignmentsTable = new TableView<>();
            policyAssignmentsTable.getStyleClass().add("modern-table");
            policyAssignmentsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
            policyAssignmentsTable.setPlaceholder(new Label("Select a policy to see assigned connection components"));
            policyAssignmentsTable.getColumns().addAll(
                    makeColumn("Component", PoliciesParser.ComponentAssignmentEntry::componentId, 160),
                    makeColumn("Component Enabled", PoliciesParser.ComponentAssignmentEntry::componentEnabled, 110),
                    makeColumn("Overwrites", PoliciesParser.ComponentAssignmentEntry::hasOverrides, 90)
            );

            policyAssignmentsTable.setRowFactory(tv -> {
                TableRow<PoliciesParser.ComponentAssignmentEntry> row = new TableRow<>();
                row.setOnMouseClicked(event -> {
                    if (row.isEmpty() || event.getClickCount() != 2 || event.getButton() != MouseButton.PRIMARY) {
                        return;
                    }
                    onPolicyComponentDoubleClicked.accept(row.getItem());
                });
                return row;
            });

            SplitPane split = new SplitPane(policiesTable, policyAssignmentsTable);
            split.setDividerPositions(0.68);
            VBox.setVgrow(split, Priority.ALWAYS);
            policiesContent.getChildren().add(split);
        }
        return policiesContent;
    }

    private VBox getTargetsContent() {
        if (targetsContent == null) {
            targetsContent = new VBox(4);
            targetsContent.getStyleClass().add("content-pane");

            alterAddressTable = new TableView<>();
            alterAddressTable.getStyleClass().add("modern-table");
            alterAddressTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
            alterAddressTable.setPlaceholder(new Label("No altered addresses loaded"));
            alterAddressTable.getColumns().addAll(
                    makeColumn("Altered Address", PoliciesParser.AlteredAddressEntry::address, 300),
                    makeIntegerColumn("Count", PoliciesParser.AlteredAddressEntry::count, 80)
            );

            alterAddressTable.setRowFactory(tv -> {
                TableRow<PoliciesParser.AlteredAddressEntry> row = new TableRow<>();
                // Consume right-button press so JavaFX does not change row selection.
                row.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                    if (event.getButton() == MouseButton.SECONDARY) {
                        event.consume();
                    }
                });
                return row;
            });

            targetDetailsTable = new TableView<>();
            targetDetailsTable.getStyleClass().add("modern-table");
            targetDetailsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
            targetDetailsTable.setPlaceholder(new Label("Select an altered address to see details"));
            targetDetailsTable.getColumns().addAll(
                    makeColumn("Policy", PoliciesParser.TargetDetailEntry::platformId, 300),
                    makeColumn("Custom Component", PoliciesParser.TargetDetailEntry::customComponent, 300)
            );

            targetDetailsTable.setRowFactory(tv -> {
                TableRow<PoliciesParser.TargetDetailEntry> row = new TableRow<>();
                row.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                    if (event.getButton() == MouseButton.SECONDARY) {
                        event.consume();
                    }
                });
                return row;
            });

            // Handle selection changes on left table
            alterAddressTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    onAlteredAddressSelected.accept(newVal.address());
                } else {
                    setTargetDetails(List.of());
                }
            });

            VBox.setVgrow(alterAddressTable, Priority.ALWAYS);
            VBox.setVgrow(targetDetailsTable, Priority.ALWAYS);

            SplitPane split = new SplitPane(alterAddressTable, targetDetailsTable);
            split.setDividerPositions(0.4);
            VBox.setVgrow(split, Priority.ALWAYS);
            targetsContent.getChildren().add(split);
        }
        return targetsContent;
    }

    private VBox getUsagesContent() {
        if (usageContent == null) {
            usageContent = new VBox(6);
            usageContent.getStyleClass().add("content-pane");

            usageTable = new TableView<>();
            usageTable.getStyleClass().add("modern-table");
            usageTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
            usageTable.setPlaceholder(new Label("No usages loaded"));
            usageTable.getColumns().addAll(
                    makeColumn("Usage ID", PoliciesParser.usageEntry::usageId, 150),
                    makeColumn("Device Type", PoliciesParser.usageEntry::platformBaseType, 150),
                    makeColumn("Platform Protocol", PoliciesParser.usageEntry::platformBaseProtocol, 160),
                    makeIntegerColumn("Policy Count", PoliciesParser.usageEntry::policyCount, 100)
            );

            usageTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    onUsageRowSelected.accept(newVal);
                }
            });

            usageTable.setRowFactory(tv -> {
                TableRow<PoliciesParser.usageEntry> row = new TableRow<>();
                row.setOnMouseClicked(event -> {
                    if (row.isEmpty() || event.getClickCount() != 2 || event.getButton() != MouseButton.PRIMARY) {
                        return;
                    }
                    onUsageRowDoubleClicked.accept(row.getItem());
                });
                return row;
            });

            usagePolicyAssignmentsTable = new TableView<>();
            usagePolicyAssignmentsTable.getStyleClass().add("modern-table");
            usagePolicyAssignmentsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
            usagePolicyAssignmentsTable.setPlaceholder(new Label("Select a usage to see matching policies"));
            usagePolicyAssignmentsTable.getColumns().addAll(
                    makeColumn("Policy ID", PoliciesParser.UsagePolicyEntry::policyId, 150),
                    makeColumn("Overwrites", PoliciesParser.UsagePolicyEntry::hasOverrides, 90)
            );

            // Add double-click handler for usage policy assignments
            usagePolicyAssignmentsTable.setRowFactory(tv -> {
                TableRow<PoliciesParser.UsagePolicyEntry> row = new TableRow<>();
                row.setOnMouseClicked(event -> {
                    if (row.isEmpty() || event.getClickCount() != 2 || event.getButton() != MouseButton.PRIMARY) {
                        return;
                    }
                    onUsagePolicyDoubleClicked.accept(row.getItem());
                });
                return row;
            });

            SplitPane split = new SplitPane(usageTable, usagePolicyAssignmentsTable);
            split.setDividerPositions(0.62);
            VBox.setVgrow(split, Priority.ALWAYS);
            usageContent.getChildren().add(split);
        }
        return usageContent;
    }

    private VBox getPsmContent() {
        if (psmContent == null) {
            psmContent = new VBox();
            psmContent.getStyleClass().add("content-pane");

            psmTable = new TableView<>();
            psmTable.getStyleClass().add("modern-table");
            psmTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
            psmTable.setPlaceholder(new Label("No PSM servers loaded"));
            psmTable.getColumns().addAll(
                    makeColumn("ID", PVConfigurationParser.PSMServerEntry::id, 120),
                    makeColumn("Name", PVConfigurationParser.PSMServerEntry::name, 220),
                    makeColumn("Server Address", PVConfigurationParser.PSMServerEntry::serverAddress, 160),
                    makeColumn("Port", PVConfigurationParser.PSMServerEntry::serverPort, 60),
                    makeColumn("TS Gateway", PVConfigurationParser.PSMServerEntry::tsGatewayAddress, 160),
                    makeColumn("TS Enabled", PVConfigurationParser.PSMServerEntry::tsGatewayEnable, 80)
            );

            VBox.setVgrow(psmTable, Priority.ALWAYS);
            psmContent.getChildren().add(psmTable);
        }
        return psmContent;
    }

    private VBox getPsmpContent() {
        if (psmpContent == null) {
            psmpContent = new VBox(4);
            psmpContent.getStyleClass().add("content-pane");

            psmpTable = new TableView<>();
            psmpTable.getStyleClass().add("modern-table");
            psmpTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
            psmpTable.setPlaceholder(new Label("No PSMP servers loaded"));
            psmpTable.getColumns().addAll(
                    makeColumn("ID", PVConfigurationParser.PSMPServerEntry::id, 120),
                    makeColumn("Name", PVConfigurationParser.PSMPServerEntry::name, 220),
                    makeColumn("Server Address", PVConfigurationParser.PSMPServerEntry::serverAddress, 160),
                    makeColumn("Port", PVConfigurationParser.PSMPServerEntry::serverPort, 60)
            );

            VBox.setVgrow(psmpTable, Priority.ALWAYS);
            psmpContent.getChildren().add(psmpTable);
        }
        return psmpContent;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setPadding(new Insets(6));

        sourceStatusLabel = new Label("Source: none");
        sourceStatusLabel.getStyleClass().add("status-source-label");

        pvLoadStatusLabel = new Label("PVConfiguration.xml: never loaded");
        pvLoadStatusLabel.getStyleClass().add("status-load-label");

        policiesLoadStatusLabel = new Label("Policies.xml: never loaded");
        policiesLoadStatusLabel.getStyleClass().add("status-load-label");

        Button updateBtn = new Button("Update Current");
        updateBtn.setOnAction(event -> onRefreshCurrentRequested.run());

        Button reloadAllBtn = new Button("Reload All");
        reloadAllBtn.setOnAction(event -> onReloadAllRequested.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusBar.getChildren().addAll(
                sourceStatusLabel,
                pvLoadStatusLabel,
                policiesLoadStatusLabel,
                spacer,
                updateBtn,
                reloadAllBtn
        );
        return statusBar;
    }

    void clearDataTables() {
        if (connectionComponentTable != null) {
            connectionComponentTable.setItems(FXCollections.observableArrayList());
        }
        if (connectionAssignmentTable != null) {
            connectionAssignmentTable.setItems(FXCollections.observableArrayList());
        }
        if (policyAssignmentsTable != null) {
            policyAssignmentsTable.setItems(FXCollections.observableArrayList());
        }
        if (psmTable != null) {
            psmTable.setItems(FXCollections.observableArrayList());
        }
        if (psmpTable != null) {
            psmpTable.setItems(FXCollections.observableArrayList());
        }
        if (policiesTable != null) {
            policiesTable.setItems(FXCollections.observableArrayList());
        }
        if (usageTable != null) {
            usageTable.setItems(FXCollections.observableArrayList());
        }
        if (usagePolicyAssignmentsTable != null) {
            usagePolicyAssignmentsTable.setItems(FXCollections.observableArrayList());
        }
        if (alterAddressTable != null) {
            alterAddressTable.setItems(FXCollections.observableArrayList());
        }
        if (targetDetailsTable != null) {
            targetDetailsTable.setItems(FXCollections.observableArrayList());
        }
    }

    void setConnectionAssignments(List<PoliciesParser.ComponentAssignmentEntry> rows) {
        if (connectionAssignmentTable == null) {
            return;
        }
        connectionAssignmentTable.setItems(FXCollections.observableArrayList(rows == null ? List.of() : rows));
    }

    void setPolicyAssignments(List<PoliciesParser.ComponentAssignmentEntry> rows) {
        if (policyAssignmentsTable == null) {
            return;
        }
        policyAssignmentsTable.setItems(FXCollections.observableArrayList(rows == null ? List.of() : rows));
    }

    void setUsagePolicyAssignments(List<PoliciesParser.UsagePolicyEntry> rows) {
        if (usagePolicyAssignmentsTable == null) {
            return;
        }
        usagePolicyAssignmentsTable.setItems(FXCollections.observableArrayList(rows == null ? List.of() : rows));
    }

    void setTargetDetails(List<PoliciesParser.TargetDetailEntry> rows) {
        if (targetDetailsTable == null) {
            return;
        }
        targetDetailsTable.setItems(FXCollections.observableArrayList(rows == null ? List.of() : rows));
    }

    void showToast(String message) {
        if (toastContainer == null) {
            return;
        }
        String text = (message == null || message.isBlank()) ? "Action finished." : message;

        Label toast = new Label(text);
        toast.getStyleClass().add("toast-message");
        toast.setWrapText(true);
        toast.setMaxWidth(440);
        toast.setMouseTransparent(true);

        toastContainer.getChildren().add(toast);
        PauseTransition delay = new PauseTransition(Duration.seconds(3));
        delay.setOnFinished(e -> toastContainer.getChildren().remove(toast));
        delay.play();
    }

    void refreshCurrentTabContent() {
        reloadCurrentTab();
    }

    String getSelectedTabName() {
        if (tabPane == null || tabPane.getSelectionModel().getSelectedItem() == null) {
            return "";
        }
        return tabPane.getSelectionModel().getSelectedItem().getText();
    }

    void setLoadStatus(String sourceName, String pvLabelText, boolean pvStale, String policiesLabelText, boolean policiesStale) {
        if (sourceStatusLabel != null) {
            sourceStatusLabel.setText("Source: " + ((sourceName == null || sourceName.isBlank()) ? "none" : sourceName));
        }
        if (pvLoadStatusLabel != null) {
            pvLoadStatusLabel.setText((pvLabelText == null || pvLabelText.isBlank()) ? "PVConfiguration.xml: never loaded" : pvLabelText);
            setStaleStyle(pvLoadStatusLabel, pvStale);
        }
        if (policiesLoadStatusLabel != null) {
            policiesLoadStatusLabel.setText((policiesLabelText == null || policiesLabelText.isBlank()) ? "Policies.xml: never loaded" : policiesLabelText);
            setStaleStyle(policiesLoadStatusLabel, policiesStale);
        }
    }

    private void setStaleStyle(Label label, boolean stale) {
        if (stale) {
            if (!label.getStyleClass().contains("status-stale")) {
                label.getStyleClass().add("status-stale");
            }
        } else {
            label.getStyleClass().remove("status-stale");
        }
    }

    private void startStatusPolling() {
        stopStatusPolling();
        statusPoller = new Timeline(new KeyFrame(STATUS_POLL_INTERVAL, event -> onStatusRefreshRequested.run()));
        statusPoller.setCycleCount(Timeline.INDEFINITE);
        statusPoller.play();
    }

    private void stopStatusPolling() {
        if (statusPoller != null) {
            statusPoller.stop();
            statusPoller = null;
        }
    }

    private void applyProfilesToSidebar() {
        suppressSideNavCallbacks = true;
        sideNav.setProfiles(settings.getSourceProfiles(), settings.getActiveProfileId());
        suppressSideNavCallbacks = false;
    }

    void applyTheme(Scene scene) {
        ThemeManager.ThemeOption resolvedTheme = resolveActiveTheme();
        applyTheme(scene, resolvedTheme);
    }

    private void applyTheme(Scene scene, ThemeManager.ThemeOption theme) {
        if (scene == null) {
            return;
        }
        if (availableThemes.isEmpty()) {
            refreshAvailableThemes();
        }

        if (theme == null) {
            scene.getStylesheets().clear();
            return;
        }

        scene.getStylesheets().setAll(themeManager.buildStylesheetUris(theme));
    }

    private ThemeManager.ThemeOption resolveActiveTheme() {
        if (availableThemes.isEmpty()) {
            refreshAvailableThemes();
        }

        ThemeManager.ThemeOption resolvedTheme = themeManager.resolveTheme(settings.getTheme(), availableThemes);
        if (resolvedTheme != null) {
            settings.setTheme(resolvedTheme.id());
        }
        return resolvedTheme;
    }

    private void refreshAvailableThemes() {
        availableThemes.clear();
        availableThemes.addAll(themeManager.discoverThemes());
    }

    private void refreshThemeMenu() {
        if (themeMenu == null) {
            return;
        }

        refreshAvailableThemes();
        themeMenu.getItems().clear();

        ThemeManager.ThemeOption resolvedTheme = resolveActiveTheme();
        if (resolvedTheme != null && !resolvedTheme.id().equals(settings.getTheme())) {
            settings.setTheme(resolvedTheme.id());
            settingsStore.save(settings);
        }

        ToggleGroup themeGroup = new ToggleGroup();
        for (ThemeManager.ThemeOption theme : availableThemes) {
            RadioMenuItem item = new RadioMenuItem(theme.displayName());
            item.setToggleGroup(themeGroup);
            item.setSelected(resolvedTheme != null && theme.id().equals(resolvedTheme.id()));
            item.setOnAction(event -> activateTheme(theme.id()));
            themeMenu.getItems().add(item);
        }

        if (themeMenu.getItems().isEmpty()) {
            MenuItem emptyItem = new MenuItem("No themes available");
            emptyItem.setDisable(true);
            themeMenu.getItems().add(emptyItem);
        }

        themeMenu.getItems().add(new SeparatorMenuItem());

        MenuItem refreshThemesItem = new MenuItem("Refresh themes");
        refreshThemesItem.setOnAction(event -> {
            themeManager.refreshThemes();
            refreshThemeMenu();
            applyTheme(mainScene);
            showToast("Themes refreshed from " + themeManager.externalThemesDirectory());
        });

        MenuItem openThemesFolderItem = new MenuItem("Open themes folder");
        openThemesFolderItem.setOnAction(event -> openThemesFolder());

        MenuItem previewThemesItem = new MenuItem("Theme preview...");
        previewThemesItem.setOnAction(event -> openThemePreviewDialog());

        themeMenu.getItems().addAll(refreshThemesItem, openThemesFolderItem, previewThemesItem);
    }

    private void activateTheme(String themeId) {
        settings.setTheme(themeId);
        ThemeManager.ThemeOption selected = resolveActiveTheme();
        settingsStore.save(settings);
        applyTheme(mainScene, selected);
    }

    private void openThemePreviewDialog() {
        Stage dialog = new Stage();
        dialog.initOwner(primaryStage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Theme Preview");

        refreshAvailableThemes();
        ThemeManager.ThemeOption initialTheme = resolveActiveTheme();

        ListView<ThemeManager.ThemeOption> themeList = new ListView<>(FXCollections.observableArrayList(availableThemes));
        themeList.getStyleClass().addAll("theme-list", "source-settings-list");
        themeList.setPrefWidth(230);
        themeList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ThemeManager.ThemeOption item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(item.displayName() + (item.external() ? "  [Custom]" : ""));
            }
        });

        TabPane previewTabs = new TabPane(
                new Tab("Overview", buildPreviewOverview()),
                new Tab("Table", buildPreviewTable()),
                new Tab("Sidebar", buildPreviewSidebar())
        );
        previewTabs.getStyleClass().addAll("main-tab-pane", "preview-tabs");
        previewTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        BorderPane dialogRoot = new BorderPane();
        dialogRoot.getStyleClass().addAll("settings-dialog", "theme-preview-dialog");
        dialogRoot.setPadding(new Insets(12));
        dialogRoot.setLeft(themeList);
        dialogRoot.setCenter(previewTabs);
        BorderPane.setMargin(previewTabs, new Insets(0, 0, 0, 12));

        Button applyButton = new Button("Apply Theme");
        Button closeButton = new Button("Close");
        HBox actions = new HBox(8, applyButton, closeButton);
        actions.getStyleClass().add("dialog-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);
        dialogRoot.setBottom(actions);

        Scene previewScene = new Scene(dialogRoot, 900, 620);
        applyTheme(previewScene, initialTheme);
        dialog.setScene(previewScene);

        themeList.getSelectionModel().selectedItemProperty().addListener((obs, oldTheme, newTheme) -> {
            if (newTheme != null) {
                applyTheme(previewScene, newTheme);
            }
        });

        if (!availableThemes.isEmpty()) {
            ThemeManager.ThemeOption selectedTheme = initialTheme;
            if (selectedTheme == null) {
                selectedTheme = availableThemes.get(0);
            }
            themeList.getSelectionModel().select(selectedTheme);
        }

        applyButton.setOnAction(event -> {
            ThemeManager.ThemeOption selected = themeList.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            settings.setTheme(selected.id());
            settingsStore.save(settings);
            applyTheme(mainScene, selected);
            refreshThemeMenu();
            showToast("Applied theme: " + selected.displayName());
        });

        closeButton.setOnAction(event -> dialog.close());
        dialog.showAndWait();
    }

    private VBox buildPreviewOverview() {
        VBox pane = new VBox(12);
        pane.getStyleClass().add("content-pane");
        pane.setPadding(new Insets(14));

        Label title = new Label("Theme Preview");
        title.getStyleClass().add("preview-title");
        Label subtitle = new Label("Check colors, spacing and contrast before applying the theme globally.");
        subtitle.getStyleClass().add("preview-subtitle");

        TextField field = new TextField("PSM-SRV-01");
        field.setPromptText("Search...");
        Button primary = new Button("Primary Action");
        Button secondary = new Button("Secondary");
        secondary.getStyleClass().add("secondary-button");
        HBox controls = new HBox(8, field, primary, secondary);
        HBox.setHgrow(field, Priority.ALWAYS);

        Label info = new Label("This panel previews common controls used across the app.");
        info.getStyleClass().add("preview-note");

        pane.getChildren().addAll(title, subtitle, controls, info);
        return pane;
    }

    private VBox buildPreviewTable() {
        VBox pane = new VBox(8);
        pane.getStyleClass().add("content-pane");
        pane.setPadding(new Insets(12));

        TableView<PreviewRow> table = new TableView<>();
        table.getStyleClass().add("modern-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getColumns().addAll(
                makePreviewColumn("Host", PreviewRow::host),
                makePreviewColumn("Zone", PreviewRow::zone),
                makePreviewColumn("Status", PreviewRow::status)
        );
        table.setItems(FXCollections.observableArrayList(
                new PreviewRow("psm-01", "Prod", "Online"),
                new PreviewRow("psm-02", "DR", "Online"),
                new PreviewRow("psm-03", "Lab", "Maintenance")
        ));
        VBox.setVgrow(table, Priority.ALWAYS);
        pane.getChildren().add(table);
        return pane;
    }

    private VBox buildPreviewSidebar() {
        VBox pane = new VBox(10);
        pane.getStyleClass().add("content-pane");
        pane.setPadding(new Insets(12));

        Label title = new Label("Sidebar Sample");
        title.getStyleClass().add("preview-title");

        ListView<String> nav = new ListView<>(FXCollections.observableArrayList(
                "Production Vault",
                "Disaster Recovery",
                "Development Vault",
                "Training Vault"
        ));
        nav.getStyleClass().addAll("side-nav-list", "preview-sidebar-list");
        nav.getSelectionModel().select(0);

        VBox.setVgrow(nav, Priority.ALWAYS);
        pane.getChildren().addAll(title, nav);
        return pane;
    }

    private TableColumn<PreviewRow, String> makePreviewColumn(String header, java.util.function.Function<PreviewRow, String> getter) {
        TableColumn<PreviewRow, String> column = new TableColumn<>(header);
        column.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(getter.apply(data.getValue())));
        return column;
    }

    private <T> TableColumn<T, String> makeColumn(
            String header,
            java.util.function.Function<T, String> getter,
            double width) {
        TableColumn<T, String> col = new TableColumn<>();
        col.setPrefWidth(width);
        col.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(getter.apply(data.getValue())));

        Label label = new Label(header);
        label.getStyleClass().add("table-header-title");

        TextField filterField = new TextField();
        filterField.getStyleClass().add("column-filter-field");
        filterField.setPromptText("Filter...");
        filterField.setPrefHeight(22);
        filterField.setMaxWidth(Double.MAX_VALUE);

        VBox headerBox = new VBox(2, label, filterField);
        headerBox.getStyleClass().add("table-header-box");
        col.setGraphic(headerBox);
        col.setUserData(filterField);

        return col;
    }

    private <T> TableColumn<T, Integer> makeIntegerColumn(
            String header,
            java.util.function.Function<T, Integer> getter,
            double width) {
        TableColumn<T, Integer> col = new TableColumn<>();
        col.setPrefWidth(width);
        col.setCellValueFactory(data -> {
            Integer value = getter.apply(data.getValue());
            return new javafx.beans.property.SimpleObjectProperty<>(value);
        });

        Label label = new Label(header);
        label.getStyleClass().add("table-header-title");

        TextField filterField = new TextField();
        filterField.getStyleClass().add("column-filter-field");
        filterField.setPromptText("Filter...");
        filterField.setPrefHeight(22);
        filterField.setMaxWidth(Double.MAX_VALUE);

        VBox headerBox = new VBox(2, label, filterField);
        headerBox.getStyleClass().add("table-header-box");
        col.setGraphic(headerBox);
        col.setUserData(filterField);

        return col;
    }

    /**
     * Opens the single About window. If it already exists it is simply brought to the front, so
     * at most one About window can be visible at a time.
     */
    private void openAboutDialog() {
        if (aboutStage != null) {
            aboutStage.toFront();
            aboutStage.requestFocus();
            return;
        }

        Stage dialog = new Stage();
        dialog.initOwner(primaryStage);
        dialog.setTitle("About " + APP_NAME);
        dialog.setResizable(false);
        if (primaryStage != null) {
            dialog.getIcons().setAll(primaryStage.getIcons());
        }
        dialog.setOnHidden(event -> aboutStage = null);

        Scene scene = new Scene(buildAboutContent(), 460, 420);
        applyTheme(scene);
        dialog.setScene(scene);

        aboutStage = dialog;
        dialog.show();
    }

    private VBox buildAboutContent() {
        VBox container = new VBox(12);
        container.getStyleClass().addAll("content-pane", "about-dialog");
        container.setPadding(new Insets(20));

        Label title = new Label(APP_NAME);
        title.getStyleClass().add("about-title");

        Label version = new Label("Version " + APP_VERSION);
        version.getStyleClass().add("about-version");

        Label description = new Label(
                "Open-source administrator tool for managing CyberArk Self-Hosted PAM "
                        + "configurations and policies. Browse connection components, policies, "
                        + "usages, altered addresses and PSM/PSMP servers from your PVConfiguration.xml "
                        + "and Policies.xml files.");
        description.setWrapText(true);
        description.getStyleClass().add("about-description");

        Label disclaimer = new Label(
                "Not affiliated with, endorsed by, or supported by CyberArk Software Ltd.");
        disclaimer.setWrapText(true);
        disclaimer.getStyleClass().add("about-disclaimer");

        Label projectLabel = new Label("Project:");
        projectLabel.getStyleClass().add("about-section-label");

        Hyperlink githubLink = new Hyperlink(GITHUB_URL);
        githubLink.getStyleClass().add("about-link");
        githubLink.setOnAction(event -> openInBrowser(GITHUB_URL));

        HBox linkRow = new HBox(6, projectLabel, githubLink);
        linkRow.setAlignment(Pos.CENTER_LEFT);

        // Placeholder for a future donate / support banner (Buy Me a Coffee, etc.).
        VBox supportArea = new VBox(4);
        supportArea.getStyleClass().add("about-support");
        Label supportLabel = new Label("Support is coming soon.");
        supportLabel.getStyleClass().add("about-support-note");
        supportArea.getChildren().add(supportLabel);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button closeButton = new Button("Close");
        closeButton.setOnAction(event -> {
            if (aboutStage != null) {
                aboutStage.close();
            }
        });
        HBox actions = new HBox(closeButton);
        actions.getStyleClass().add("dialog-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);

        container.getChildren().addAll(
                title, version, description, disclaimer, linkRow, supportArea, spacer, actions);
        return container;
    }

    /** Opens a URL in the user's default browser, falling back to a toast if unsupported. */
    private void openInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (IOException | URISyntaxException | RuntimeException e) {
            // Fall through to the toast below.
        }
        showToast("Open in your browser: " + url);
    }

    private void openThemesFolder() {
        Path themesDirectory = themeManager.externalThemesDirectory();
        try {
            Files.createDirectories(themesDirectory);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(themesDirectory.toFile());
            } else {
                showToast("Themes folder: " + themesDirectory);
            }
        } catch (IOException | UnsupportedOperationException e) {
            showToast("Cannot open themes folder: " + themesDirectory);
        }
    }

    private void reloadCurrentTab() {
        if (tabPane == null) {
            return;
        }
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null) {
            return;
        }
        String text = selectedTab.getText();
        if (TAB_CONNECTION_COMPONENTS.equals(text)) {
            root.setCenter(getConnectionComponentContent());
            onConnectionComponentTabSelected.run();
        } else if (TAB_POLICIES.equals(text)) {
            root.setCenter(getPoliciesContent());
            onPoliciesTabSelected.run();
        } else if (TAB_USAGES.equals(text)) {
            root.setCenter(getUsagesContent());
            onUsagesTabSelected.run();
        } else if (TAB_PSMS.equals(text)) {
            root.setCenter(getPsmContent());
            onPsmTabSelected.run();
        } else if (TAB_PSMPS.equals(text)) {
            root.setCenter(getPsmpContent());
            onPsmpTabSelected.run();
        } else if (TAB_ALTER_ADDRESSES.equals(text)) {
            root.setCenter(getTargetsContent());
            onTargetsTabSelected.run();
        }
    }

    private void openSourcesSettingsDialog() {
        Stage dialog = new Stage();
        dialog.initOwner(primaryStage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Source Settings");

        ObservableList<AppSettings.SourceProfile> draftProfiles = FXCollections.observableArrayList(copyProfiles(settings.getSourceProfiles()));
        ListView<AppSettings.SourceProfile> listView = new ListView<>(draftProfiles);
        listView.getStyleClass().add("source-settings-list");
        listView.setCellFactory(lv -> new SourceListCell(draftProfiles, listView));

        TextField displayNameField = new TextField();
        TextField shortLabelField = new TextField();
        TextField folderPathField = new TextField();
        folderPathField.setPromptText("e.g. \\\\prodserver1\\c$\\programfiles\\cyberark\\pvwa\\temp\\ or .\\prod\\");

        Label activeLabel = new Label();
        activeLabel.getStyleClass().add("active-source-label");
        final String[] activeId = {settings.getActiveProfileId()};
        Runnable refreshActiveLabel = () -> {
            AppSettings.SourceProfile active = null;
            for (AppSettings.SourceProfile profile : draftProfiles) {
                if (profile.id().equals(activeId[0])) {
                    active = profile;
                    break;
                }
            }
            activeLabel.setText(active == null ? "Active source: none" : "Active source: " + displayName(active));
        };
        refreshActiveLabel.run();

        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            fillProfileForm(newVal, displayNameField, shortLabelField, folderPathField);
        });

        Button browseButton = new Button("Browse...");
        final File[] lastBrowsedDirectory = {existingDirectory(folderPathField.getText())};
        browseButton.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            File initialDirectory = existingDirectory(folderPathField.getText());
            if (initialDirectory == null) {
                initialDirectory = lastBrowsedDirectory[0];
            }
            if (initialDirectory == null) {
                AppSettings.SourceProfile activeProfile = settings.getActiveProfile();
                initialDirectory = existingDirectory(activeProfile == null ? "" : activeProfile.folderPath());
            }
            if (initialDirectory != null) {
                chooser.setInitialDirectory(initialDirectory);
            }
            File selectedDirectory = chooser.showDialog(dialog);
            if (selectedDirectory != null) {
                lastBrowsedDirectory[0] = selectedDirectory;
                folderPathField.setText(selectedDirectory.getPath());
            }
        });

        Button addButton = new Button("Add");
        addButton.setOnAction(event -> {
            if (draftProfiles.size() >= AppSettings.MAX_SOURCES) {
                showToast("Maximum 10 source profiles are allowed.");
                return;
            }

            AppSettings.SourceProfile profile = AppSettingsStore.newProfile("New Source");
            applyFormToProfile(profile, displayNameField, shortLabelField, folderPathField);
            if (profile.displayName() == null || profile.displayName().isBlank()) {
                profile.setDisplayName("New Source");
            }

            draftProfiles.add(profile);
            listView.getSelectionModel().select(profile);
            listView.refresh();
            refreshActiveLabel.run();
        });

        Button removeButton = new Button("Remove");
        removeButton.setOnAction(event -> {
            AppSettings.SourceProfile selected = listView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            draftProfiles.remove(selected);
            if (selected.id().equals(activeId[0])) {
                activeId[0] = draftProfiles.isEmpty() ? null : draftProfiles.get(0).id();
                refreshActiveLabel.run();
            }
        });

        Button setActiveButton = new Button("Set Selected Active");
        setActiveButton.setOnAction(event -> {
            AppSettings.SourceProfile selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                activeId[0] = selected.id();
                refreshActiveLabel.run();
            }
        });

        java.util.function.Function<Boolean, Boolean> persistDraftProfiles = closeAfterSave -> {
            AppSettings.SourceProfile selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                applyFormToProfile(selected, displayNameField, shortLabelField, folderPathField);
                listView.refresh();
                refreshActiveLabel.run();
            } else if (hasAnyFormValue(displayNameField, shortLabelField, folderPathField)) {
                if (draftProfiles.size() >= AppSettings.MAX_SOURCES) {
                    showToast("Maximum 10 source profiles are allowed.");
                    return false;
                }
                AppSettings.SourceProfile profile = AppSettingsStore.newProfile("New Source");
                applyFormToProfile(profile, displayNameField, shortLabelField, folderPathField);
                if (profile.displayName() == null || profile.displayName().isBlank()) {
                    profile.setDisplayName("New Source");
                }
                draftProfiles.add(profile);
                listView.getSelectionModel().select(profile);
                listView.refresh();
                refreshActiveLabel.run();
            }

            if (draftProfiles.isEmpty()) {
                showToast("At least one source profile is required.");
                return false;
            }
            for (AppSettings.SourceProfile profile : draftProfiles) {
                if (profile.displayName() == null || profile.displayName().isBlank()) {
                    profile.setDisplayName("Unnamed source");
                }
            }

            settings.replaceSourceProfiles(copyProfiles(draftProfiles));
            settings.setActiveProfileId(activeId[0]);
            settingsStore.save(settings);
            applyProfilesToSidebar();
            onSourceProfileChanged.run();
            reloadCurrentTab();

            if (closeAfterSave) {
                dialog.close();
            } else {
                showToast("Source settings saved.");
            }
            return true;
        };

        Button applyButton = new Button("Apply");
        applyButton.setOnAction(event -> persistDraftProfiles.apply(false));

        Button saveButton = new Button("Save & Close");
        saveButton.setOnAction(event -> persistDraftProfiles.apply(true));

        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(event -> dialog.close());

        if (!draftProfiles.isEmpty()) {
            listView.getSelectionModel().select(0);
        }

        HBox pathRow = new HBox(6, folderPathField, browseButton);
        HBox.setHgrow(folderPathField, Priority.ALWAYS);

        VBox form = new VBox(8,
                new Label("Display name"), displayNameField,
                new Label("Short label (optional)"), shortLabelField,
                new Label("Folder path"), pathRow,
                activeLabel,
                setActiveButton
        );
        form.getStyleClass().add("settings-form");
        form.setPadding(new Insets(6));

        HBox buttons = new HBox(8, addButton, removeButton, applyButton, saveButton, cancelButton);
        buttons.getStyleClass().add("dialog-actions");
        buttons.setAlignment(Pos.CENTER_RIGHT);

        BorderPane dialogRoot = new BorderPane();
        dialogRoot.getStyleClass().add("settings-dialog");
        dialogRoot.setPadding(new Insets(10));
        dialogRoot.setLeft(listView);
        dialogRoot.setCenter(form);
        dialogRoot.setBottom(buttons);
        BorderPane.setMargin(listView, new Insets(0, 10, 0, 0));
        BorderPane.setMargin(buttons, new Insets(10, 0, 0, 0));
        listView.setPrefWidth(280);

        Scene dialogScene = new Scene(dialogRoot, 760, 430);
        applyTheme(dialogScene);
        dialog.setScene(dialogScene);
        dialog.showAndWait();
    }

    private List<AppSettings.SourceProfile> copyProfiles(List<AppSettings.SourceProfile> original) {
        List<AppSettings.SourceProfile> copy = new ArrayList<>();
        for (AppSettings.SourceProfile profile : original) {
            copy.add(new AppSettings.SourceProfile(profile));
        }
        return copy;
    }

    private String displayName(AppSettings.SourceProfile profile) {
        if (profile.displayName() == null || profile.displayName().isBlank()) {
            return "Unnamed source";
        }
        return profile.displayName();
    }

    private static class SourceListCell extends ListCell<AppSettings.SourceProfile> {
        private final ObservableList<AppSettings.SourceProfile> items;

        private SourceListCell(ObservableList<AppSettings.SourceProfile> items, ListView<AppSettings.SourceProfile> owner) {
            this.items = items;

            setOnDragDetected(event -> {
                if (getItem() == null) {
                    return;
                }
                Dragboard dragboard = startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString(getItem().id());
                dragboard.setContent(content);
                event.consume();
            });

            setOnDragOver(event -> {
                if (event.getDragboard().hasString()) {
                    event.acceptTransferModes(TransferMode.MOVE);
                }
                event.consume();
            });

            setOnDragDropped(event -> {
                Dragboard dragboard = event.getDragboard();
                boolean success = false;
                if (dragboard.hasString()) {
                    String draggedId = dragboard.getString();
                    AppSettings.SourceProfile dragged = findById(draggedId);
                    AppSettings.SourceProfile target = getItem();
                    if (dragged != null) {
                        int draggedIndex = items.indexOf(dragged);
                        int targetIndex = target == null ? items.size() : items.indexOf(target);
                        if (draggedIndex >= 0) {
                            items.remove(draggedIndex);
                            if (targetIndex > draggedIndex) {
                                targetIndex--;
                            }
                            targetIndex = Math.max(0, Math.min(targetIndex, items.size()));
                            items.add(targetIndex, dragged);
                            owner.getSelectionModel().select(dragged);
                            success = true;
                        }
                    }
                }
                event.setDropCompleted(success);
                event.consume();
            });
        }

        @Override
        protected void updateItem(AppSettings.SourceProfile item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                return;
            }
            String folder = item.folderPath() == null ? "" : item.folderPath().trim();
            String name = item.displayName() == null || item.displayName().isBlank() ? "Unnamed source" : item.displayName();
            setText(folder.isBlank() ? name : name + "  [" + folder + "]");
        }

        private AppSettings.SourceProfile findById(String id) {
            for (AppSettings.SourceProfile item : items) {
                if (item.id().equals(id)) {
                    return item;
                }
            }
            return null;
        }
    }

    private record PreviewRow(String host, String zone, String status) {
    }

    private File existingDirectory(String directoryPath) {
        if (directoryPath == null || directoryPath.isBlank()) {
            return null;
        }
        File file = new File(directoryPath);
        if (!file.exists()) {
            return null;
        }
        return file.isDirectory() ? file : file.getParentFile();
    }

    private void fillProfileForm(AppSettings.SourceProfile profile, TextField displayNameField, TextField shortLabelField, TextField folderPathField) {
        if (profile == null) {
            displayNameField.setText("");
            shortLabelField.setText("");
            folderPathField.setText("");
            return;
        }
        displayNameField.setText(profile.displayName() == null ? "" : profile.displayName());
        shortLabelField.setText(profile.shortLabel() == null ? "" : profile.shortLabel());
        folderPathField.setText(profile.folderPath() == null ? "" : profile.folderPath());
    }

    private void applyFormToProfile(AppSettings.SourceProfile profile, TextField displayNameField, TextField shortLabelField, TextField folderPathField) {
        if (profile == null) {
            return;
        }
        profile.setDisplayName(displayNameField.getText());
        profile.setShortLabel(shortLabelField.getText());
        profile.setFolderPath(folderPathField.getText());
    }

    private boolean hasAnyFormValue(TextField displayNameField, TextField shortLabelField, TextField folderPathField) {
        return !displayNameField.getText().isBlank()
                || !shortLabelField.getText().isBlank()
                || !folderPathField.getText().isBlank();
    }
}