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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class UI {
    private final AppSettings settings;
    private final AppSettingsStore settingsStore;

    private BorderPane root;
    private VBox toastContainer;
    private SideNav sideNav;
    private Stage primaryStage;
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
        root = new BorderPane();
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
        toastContainer = new VBox(8);
        toastContainer.setMouseTransparent(true);
        StackPane.setAlignment(toastContainer, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(toastContainer, new Insets(14));
        sceneRoot.getChildren().add(toastContainer);

        stage.setScene(new Scene(sceneRoot, 900, 600));
        stage.show();
    }

    private VBox createTopBar() {
        return new VBox(createMenuBar(), createTabPane());
    }

    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();

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
        SettingsMenu.getItems().addAll(sourcesItem, new MenuItem("Theme"));

        Menu ViewMenu = new Menu("View");
        Menu helpMenu = new Menu("Help");

        menuBar.getMenus().addAll(fileMenu, EditMenu, SettingsMenu, ViewMenu, helpMenu);
        return menuBar;
    }

    private TabPane createTabPane() {
        tabPane = new TabPane();
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
        container.setPadding(new Insets(8));

        TableView<?> table = new TableView<>();
        table.getColumns().addAll(
                new TableColumn<>("Platform ID"),
                new TableColumn<>("Enabled")
        );
        table.setPlaceholder(new Label("No platforms loaded"));
        VBox.setVgrow(table, Priority.ALWAYS);
        container.getChildren().add(table);
        return container;
    }

    private VBox createTargetsContent() {
        VBox container = new VBox(4);
        container.setPadding(new Insets(8));
        container.getChildren().add(new Label("Targets — coming soon"));
        return container;
    }

    private VBox getConnectionComponentContent() {
        if (connectionComponentContent == null) {
            connectionComponentContent = new VBox();

            connectionComponentTable = new TableView<>();
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
                    if (!row.isEmpty()) {
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

            psmTable = new TableView<>();
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

            psmpTable = new TableView<>();
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
        toast.setStyle("-fx-background-color: rgba(30,30,30,0.95); -fx-text-fill: white; -fx-padding: 10 14 10 14; -fx-background-radius: 8;");
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
        listView.setCellFactory(lv -> new SourceListCell(draftProfiles, listView));

        TextField displayNameField = new TextField();
        TextField shortLabelField = new TextField();
        TextField folderPathField = new TextField();
        folderPathField.setPromptText("e.g. \\\\prodserver1\\c$\\programfiles\\cyberark\\pvwa\\temp\\ or .\\prodlu\\");

        Label activeLabel = new Label();
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
        form.setPadding(new Insets(6));

        HBox buttons = new HBox(8, addButton, removeButton, saveButton, cancelButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        BorderPane dialogRoot = new BorderPane();
        dialogRoot.setPadding(new Insets(10));
        dialogRoot.setLeft(listView);
        dialogRoot.setCenter(form);
        dialogRoot.setBottom(buttons);
        BorderPane.setMargin(listView, new Insets(0, 10, 0, 0));
        BorderPane.setMargin(buttons, new Insets(10, 0, 0, 0));
        listView.setPrefWidth(280);

        dialog.setScene(new Scene(dialogRoot, 760, 430));
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
        private final ListView<AppSettings.SourceProfile> owner;

        private SourceListCell(ObservableList<AppSettings.SourceProfile> items, ListView<AppSettings.SourceProfile> owner) {
            this.items = items;
            this.owner = owner;

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

    private <T> TableColumn<T, String> makeColumn(
            String header,
            java.util.function.Function<T, String> getter,
            double width) {
        TableColumn<T, String> col = new TableColumn<>();
        col.setPrefWidth(width);
        col.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(getter.apply(data.getValue())));

        Label label = new Label(header);
        label.setStyle("-fx-font-weight: bold;");

        TextField filterField = new TextField();
        filterField.setPromptText("Filter...");
        filterField.setPrefHeight(22);
        filterField.setMaxWidth(Double.MAX_VALUE);

        VBox headerBox = new VBox(2, label, filterField);
        col.setGraphic(headerBox);
        col.setUserData(filterField);

        return col;
    }
}
