import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.animation.PauseTransition;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class UI {
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

    private TableView<PVConfigurationParser.PSMServerEntry> psmTable;
    private VBox psmContent;
    private Runnable onPsmTabSelected = () -> {
    };
    private Runnable onSourceProfileChanged = () -> {
    };

    UI(AppSettings settings, AppSettingsStore settingsStore) {
        this.settings = settings;
        this.settingsStore = settingsStore;
        this.themeManager = new ThemeManager(settingsStore.getThemesDirectory());
    }

    void setOnSourceProfileChanged(Runnable onSourceProfileChanged) {
        this.onSourceProfileChanged = onSourceProfileChanged == null ? () -> {} : onSourceProfileChanged;
    }

    TableView<PVConfigurationParser.PSMServerEntry> getPsmTable() {
        return psmTable;
    }

    void setOnPsmTabSelected(Runnable onPsmTabSelected) {
        this.onPsmTabSelected = onPsmTabSelected == null ? () -> {
        } : onPsmTabSelected;
    }

    private TableView<PVConfigurationParser.PSMPServerEntry> psmpTable;
    private VBox psmpContent;
    private Runnable onPsmpTabSelected = () -> {
    };

    TableView<PVConfigurationParser.PSMPServerEntry> getPsmpTable() {
        return psmpTable;
    }

    void setOnPsmpTabSelected(Runnable onPsmpTabSelected) {
        this.onPsmpTabSelected = onPsmpTabSelected == null ? () -> {
        } : onPsmpTabSelected;
    }

    private TableView<PVConfigurationParser.ConnectionComponentEntry> connectionComponentTable;
    private VBox connectionComponentContent;
    private Runnable onConnectionComponentTabSelected = () -> {
    };
    private java.util.function.Consumer<PVConfigurationParser.ConnectionComponentEntry> onConnectionComponentRowSelected = entry -> {
    };

    TableView<PVConfigurationParser.ConnectionComponentEntry> getConnectionComponentTable() {
        return connectionComponentTable;
    }

    void setOnConnectionComponentTabSelected(Runnable onConnectionComponentTabSelected) {
        this.onConnectionComponentTabSelected = onConnectionComponentTabSelected == null ? () -> {
        } : onConnectionComponentTabSelected;
    }

    void setOnConnectionComponentRowSelected(java.util.function.Consumer<PVConfigurationParser.ConnectionComponentEntry> onConnectionComponentRowSelected) {
        this.onConnectionComponentRowSelected = onConnectionComponentRowSelected == null ? entry -> {
        } : onConnectionComponentRowSelected;
    }

    private TabPane tabPane;

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

        stage.setTitle("CyberArk VaultOps v1.0");
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
        stage.show();
    }

    private VBox createTopBar() {
        return new VBox(createMenuBar(), createTabPane());
    }

    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();
        menuBar.getStyleClass().add("app-menu-bar");

        Menu fileMenu = new Menu("File");
        fileMenu.getItems().addAll(
                new MenuItem("Open XML file"),
                new MenuItem("Import PSM Component"),
                new MenuItem("Export to CSV"),
                new MenuItem("Exit")
        );

        Menu EditMenu = new Menu("Edit");
        EditMenu.getItems().addAll(
                new MenuItem("Order")
        );

        Menu SettingsMenu = new Menu("Settings");
        MenuItem sourcesItem = new MenuItem("Sources");
        sourcesItem.setOnAction(event -> openSourcesSettingsDialog());
        themeMenu = new Menu("Theme");
        refreshThemeMenu();
        SettingsMenu.getItems().addAll(sourcesItem, themeMenu);

        Menu ViewMenu = new Menu("View");
        Menu helpMenu = new Menu("Help");

        menuBar.getMenus().addAll(fileMenu, EditMenu, SettingsMenu, ViewMenu, helpMenu);
        return menuBar;
    }

    private TabPane createTabPane() {
        tabPane = new TabPane();
        tabPane.getStyleClass().add("main-tab-pane");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab connectionComponents = new Tab("Connection Components");
        Tab platforms = new Tab("Platforms");
        Tab targets = new Tab("Targets");
        Tab psms = new Tab("PSMs");
        Tab psmps = new Tab("PSMPs");

        tabPane.getTabs().addAll(connectionComponents, platforms, targets, psms, psmps);

        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (root == null) return;
            if (newTab == connectionComponents) {
                root.setCenter(getConnectionComponentContent());
                onConnectionComponentTabSelected.run();
            } else if (newTab == platforms) root.setCenter(createPlatformsContent());
            else if (newTab == targets) root.setCenter(createTargetsContent());
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

    private VBox createPlatformsContent() {
        VBox container = new VBox(4);
        container.getStyleClass().add("content-pane");
        container.setPadding(new Insets(8));

        TableView<?> table = new TableView<>();
        table.getStyleClass().add("modern-table");
        table.getColumns().addAll(
                new TableColumn<>("Platform ID"),
                new TableColumn<>("Enabled"),
                new TableColumn<>("Overwrite")
        );
        table.setPlaceholder(new Label("No platforms loaded"));
        VBox.setVgrow(table, Priority.ALWAYS);
        container.getChildren().add(table);
        return container;
    }

    private VBox createTargetsContent() {
        VBox container = new VBox(4);
        container.getStyleClass().add("content-pane");
        container.setPadding(new Insets(8));
        container.getChildren().add(new Label("Targets — coming soon"));
        return container;
    }

    private VBox getConnectionComponentContent() {
        if (connectionComponentContent == null) {
            connectionComponentContent = new VBox();
            connectionComponentContent.getStyleClass().add("content-pane");

            connectionComponentTable = new TableView<>();
            connectionComponentTable.getStyleClass().add("modern-table");
            connectionComponentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
            connectionComponentTable.setPlaceholder(new Label("No Connection Component loaded"));
            connectionComponentTable.getColumns().addAll(
                    makeColumn("ID", PVConfigurationParser.ConnectionComponentEntry::id, 120),
                    makeColumn("Name", PVConfigurationParser.ConnectionComponentEntry::name, 220),
                    makeColumn("Client App", PVConfigurationParser.ConnectionComponentEntry::ClientApp, 160),
                    makeColumn("Client Dispatcher", PVConfigurationParser.ConnectionComponentEntry::ClientDispatcher, 200)
            );

            connectionComponentTable.setRowFactory(tv -> {
                TableRow<PVConfigurationParser.ConnectionComponentEntry> row = new TableRow<>();
                row.setOnMouseClicked(event -> {
                    if (!row.isEmpty() && event.getClickCount() == 2) {
                        onConnectionComponentRowSelected.accept(row.getItem());
                    }
                });
                return row;
            });

            VBox.setVgrow(connectionComponentTable, Priority.ALWAYS);
            connectionComponentContent.getChildren().add(connectionComponentTable);

        }
        return connectionComponentContent;
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

    private VBox createPlatformTableArea() {
        VBox box = new VBox(4);
        Label title = new Label("Platforms");

        TableView<?> table = new TableView<>();
        table.getColumns().addAll(
                new TableColumn<>("Platform ID"),
                new TableColumn<>("Enabled")
        );
        VBox.setVgrow(table, Priority.ALWAYS);
        box.getChildren().addAll(title, table);
        return box;
    }


    private HBox createStatusBar() {
        HBox statusBar = new HBox(16);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setPadding(new Insets(6));

        Label lastUpdate = new Label("Last update time: 2026-05-06 11:34");
        Button updateBtn = new Button("Update");

        statusBar.getChildren().addAll(lastUpdate, updateBtn);
        return statusBar;
    }

    void clearDataTables() {
        if (connectionComponentTable != null) {
            connectionComponentTable.setItems(FXCollections.observableArrayList());
        }
        if (psmTable != null) {
            psmTable.setItems(FXCollections.observableArrayList());
        }
        if (psmpTable != null) {
            psmpTable.setItems(FXCollections.observableArrayList());
        }
    }

    void showToast(String message) {
        if (toastContainer == null) {
            return;
        }
        String text = (message == null || message.isBlank()) ? "Load failed." : message;

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
            item.setOnAction(event -> activateTheme(theme.id(), true));
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

    private void activateTheme(String themeId, boolean persistSelection) {
        settings.setTheme(themeId);
        ThemeManager.ThemeOption selected = resolveActiveTheme();
        if (persistSelection) {
            settingsStore.save(settings);
        }
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
        if ("Connection Components".equals(text)) {
            onConnectionComponentTabSelected.run();
        } else if ("PSMs".equals(text)) {
            onPsmTabSelected.run();
        } else if ("PSMPs".equals(text)) {
            onPsmpTabSelected.run();
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
        folderPathField.setPromptText("e.g. \\\\prodserver1\\c$\\programfiles\\cyberark\\pvwa\\temp\\ or .\\prodlu\\");

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

        final boolean[] writingSelection = {false};

        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            writingSelection[0] = true;
            if (newVal == null) {
                displayNameField.setText("");
                shortLabelField.setText("");
                folderPathField.setText("");
            } else {
                displayNameField.setText(newVal.displayName());
                shortLabelField.setText(newVal.shortLabel());
                folderPathField.setText(newVal.folderPath());
            }
            writingSelection[0] = false;
        });

        displayNameField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (writingSelection[0]) {
                return;
            }
            AppSettings.SourceProfile selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                selected.setDisplayName(newVal);
                listView.refresh();
                refreshActiveLabel.run();
            }
        });

        shortLabelField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (writingSelection[0]) {
                return;
            }
            AppSettings.SourceProfile selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                selected.setShortLabel(newVal);
                listView.refresh();
            }
        });

        folderPathField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (writingSelection[0]) {
                return;
            }
            AppSettings.SourceProfile selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                selected.setFolderPath(newVal);
                listView.refresh();
            }
        });

        Button browseButton = new Button("Browse...");
        browseButton.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            File selectedDirectory = chooser.showDialog(dialog);
            if (selectedDirectory != null) {
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
            draftProfiles.add(profile);
            listView.getSelectionModel().select(profile);
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

        Button saveButton = new Button("Save");
        saveButton.setOnAction(event -> {
            if (draftProfiles.isEmpty()) {
                showToast("At least one source profile is required.");
                return;
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
            dialog.close();
        });

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

        HBox buttons = new HBox(8, addButton, removeButton, saveButton, cancelButton);
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
}