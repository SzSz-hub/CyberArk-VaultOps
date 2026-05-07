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

import java.util.Map;

public class AppController {

    private final UI ui;
    private final PVConfigurationParser pvParser = new PVConfigurationParser();
    private boolean connectionComponentLoaded;
    private boolean psmpLoaded;
    private boolean psmLoaded;

    private static final String PV_CONFIG_PATH = "";

    public AppController(UI ui) {
        this.ui = ui;
    }

    public void loadAll() {
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
            ObservableList<PVConfigurationParser.ConnectionComponentEntry> masterData =
                    FXCollections.observableArrayList(pvParser.GetConnectionComponents(PV_CONFIG_PATH));
            wireFiltering(ui.getConnectionComponentTable(), masterData);
            connectionComponentLoaded = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadPSMPServersIfNeeded() {
        if (psmpLoaded || ui.getPsmpTable() == null) {
            return;
        }

        try {
            ObservableList<PVConfigurationParser.PSMPServerEntry> masterData =
                    FXCollections.observableArrayList(pvParser.getPSMPServers(PV_CONFIG_PATH));
            wireFiltering(ui.getPsmpTable(), masterData);
            psmpLoaded = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadPSMServersIfNeeded() {
        if (psmLoaded || ui.getPsmTable() == null) {
            return;
        }

        try {
            ObservableList<PVConfigurationParser.PSMServerEntry> masterData =
                    FXCollections.observableArrayList(pvParser.getPSMServers(PV_CONFIG_PATH));
            wireFiltering(ui.getPsmTable(), masterData);
            psmLoaded = true;
        } catch (Exception e) {
            e.printStackTrace();
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
}