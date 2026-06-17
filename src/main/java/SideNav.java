import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class SideNav extends VBox {
    private static final double EXPANDED_WIDTH = 220;
    private static final double COLLAPSED_WIDTH = 68;

    private final BooleanProperty expanded = new SimpleBooleanProperty(false);
    private final ObservableList<AppSettings.SourceProfile> profiles = FXCollections.observableArrayList();
    private final ListView<AppSettings.SourceProfile> listView = new ListView<>(profiles);
    private Consumer<AppSettings.SourceProfile> onProfileSelected = profile -> {};
    private Consumer<List<AppSettings.SourceProfile>> onProfilesReordered = reordered -> {};
    private Map<String, String> effectiveShortLabels = Map.of();

    public SideNav() {
        getStyleClass().add("side-nav");
        setPrefWidth(COLLAPSED_WIDTH);
        setSpacing(6);

        Button toggle = new Button("≡");
        toggle.prefWidthProperty().bind(prefWidthProperty().subtract(12));  //padding 6 + 6
        toggle.setMaxWidth(Double.MAX_VALUE);
        toggle.getStyleClass().add("side-nav-toggle");
        toggle.setOnAction(e -> toggle());

        listView.getStyleClass().add("side-nav-list");
        listView.setCellFactory(lv -> new DraggableProfileCell());
        listView.setPlaceholder(new Label("No sources"));
        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                onProfileSelected.accept(new AppSettings.SourceProfile(newVal));
            }
        });
        VBox.setVgrow(listView, Priority.ALWAYS);

        getChildren().addAll(toggle, listView);
    }

    public void setOnProfileSelected(Consumer<AppSettings.SourceProfile> onProfileSelected) {
        this.onProfileSelected = onProfileSelected == null ? profile -> {} : onProfileSelected;
    }

    public void setOnProfilesReordered(Consumer<List<AppSettings.SourceProfile>> onProfilesReordered) {
        this.onProfilesReordered = onProfilesReordered == null ? reordered -> {} : onProfilesReordered;
    }

    public void setProfiles(List<AppSettings.SourceProfile> items, String activeProfileId) {
        profiles.clear();
        if (items != null) {
            for (AppSettings.SourceProfile item : items) {
                profiles.add(new AppSettings.SourceProfile(item));
            }
        }
        effectiveShortLabels = AppSettings.buildEffectiveShortLabels(profiles);
        listView.refresh();
        selectProfile(activeProfileId);
    }

    public void selectProfile(String profileId) {
        if (profiles.isEmpty()) {
            return;
        }

        if (profileId != null && !profileId.isBlank()) {
            for (AppSettings.SourceProfile profile : profiles) {
                if (profile.id().equals(profileId)) {
                    listView.getSelectionModel().select(profile);
                    return;
                }
            }
        }

        listView.getSelectionModel().select(0);
    }

    private void toggle() {
        double targetWidth = expanded.get() ? COLLAPSED_WIDTH : EXPANDED_WIDTH;

        Timeline timeline = new Timeline(
                new KeyFrame(
                        Duration.millis(200),
                        new KeyValue(prefWidthProperty(), targetWidth)
                )
        );

        expanded.set(!expanded.get());
        timeline.play();
        listView.refresh();
    }

    private class DraggableProfileCell extends ListCell<AppSettings.SourceProfile> {
        DraggableProfileCell() {
            setOnDragDetected(event -> {
                if (getItem() == null) {
                    return;
                }
                Dragboard db = startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString(getItem().id());
                db.setContent(content);
                event.consume();
            });

            setOnDragOver(event -> {
                if (event.getDragboard().hasString()) {
                    event.acceptTransferModes(TransferMode.MOVE);
                }
                event.consume();
            });

            setOnDragDropped(event -> {
                Dragboard db = event.getDragboard();
                boolean success = false;
                if (db.hasString()) {
                    String draggedId = db.getString();
                    AppSettings.SourceProfile draggedItem = findById(draggedId);
                    AppSettings.SourceProfile targetItem = getItem();

                    if (draggedItem != null) {
                        int draggedIndex = profiles.indexOf(draggedItem);
                        int targetIndex = targetItem == null ? profiles.size() : profiles.indexOf(targetItem);
                        if (draggedIndex >= 0) {
                            profiles.remove(draggedIndex);
                            if (targetIndex > draggedIndex) {
                                targetIndex--;
                            }
                            targetIndex = Math.max(0, Math.min(targetIndex, profiles.size()));
                            profiles.add(targetIndex, draggedItem);
                            effectiveShortLabels = AppSettings.buildEffectiveShortLabels(profiles);
                            listView.getSelectionModel().select(draggedItem);
                            onProfilesReordered.accept(copyProfiles(profiles));
                            listView.refresh();
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
                setTooltip(null);
                return;
            }

            String label = expanded.get()
                    ? defaultDisplayName(item)
                    : effectiveShortLabels.getOrDefault(item.id(), "SRC");
            setText(label);
            setTooltip(new Tooltip(defaultDisplayName(item)));
        }

        private AppSettings.SourceProfile findById(String id) {
            for (AppSettings.SourceProfile profile : profiles) {
                if (profile.id().equals(id)) {
                    return profile;
                }
            }
            return null;
        }
    }

    private String defaultDisplayName(AppSettings.SourceProfile profile) {
        if (profile.displayName() == null || profile.displayName().isBlank()) {
            return "Unnamed source";
        }
        return profile.displayName();
    }

    private List<AppSettings.SourceProfile> copyProfiles(List<AppSettings.SourceProfile> original) {
        List<AppSettings.SourceProfile> copy = new ArrayList<>();
        for (AppSettings.SourceProfile profile : original) {
            copy.add(new AppSettings.SourceProfile(profile));
        }
        return copy;
    }
}
