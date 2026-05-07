import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class UI {
    private BorderPane root;
    private TableView<PVConfigurationParser.PSMServerEntry> psmTable;
    private VBox psmContent;
    private Runnable onPsmTabSelected = () -> {
    };

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
        root = new BorderPane();
        root.setTop(createTopBar());
        root.setLeft(new SideNav());
        root.setCenter(getPsmContent());
        root.setBottom(createStatusBar());

        tabPane.getSelectionModel().select(0);

        stage.setTitle("CyberArk VaultOps v1.0");
        stage.setScene(new Scene(root, 900, 600));
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
        SettingsMenu.getItems().addAll(
                new MenuItem("Sources"),
                new MenuItem("Theme")
        );

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
                    if (event.getClickCount() == 2 && !row.isEmpty()) {
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
