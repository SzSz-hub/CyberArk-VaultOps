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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.Consumer;

public class AppController {

    private final UI ui;
    private final AppSettings settings;
    private final Consumer<String> onLoadError;
    private final PVConfigurationParser pvParser = new PVConfigurationParser();
    private boolean connectionComponentLoaded;
    private boolean psmpLoaded;
    private boolean psmLoaded;

    public AppController(UI ui, AppSettings settings, Consumer<String> onLoadError) {
        this.ui = ui;
        this.settings = settings;
        this.onLoadError = onLoadError == null ? message -> {} : onLoadError;
    }

    public void loadAll() {
    }

    public void onSourceProfileChanged() {
        connectionComponentLoaded = false;
        psmpLoaded = false;
        psmLoaded = false;
        ui.clearDataTables();
    }

    public void showConnectionComponentDetails(PVConfigurationParser.ConnectionComponentEntry entry) {
        if (entry == null || entry.details() == null) {
            return;
        }

        TextArea detailsArea = new TextArea(formatConnectionComponentDetails(entry.details()));
        detailsArea.setEditable(false);
        detailsArea.setWrapText(false);
        detailsArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12;");

        BorderPane root = new BorderPane(detailsArea);
        root.setTop(new Label("Connection Component: " + entry.id()));

        Stage popup = new Stage();
        popup.setTitle("Connection Component Details");
        popup.setScene(new Scene(root, 900, 700));
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
        } catch (Exception e) {
            reportLoadError("PSM servers", e);
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
        return getActiveFolderPath().resolve("Policies.xml").toString();
    }

    private String getActivePvConfigurationPath() {
        return getActiveFolderPath().resolve("PVConfiguration.xml").toString();
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
}