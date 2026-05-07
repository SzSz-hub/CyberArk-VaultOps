import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class SideNav extends VBox {
    private static final double EXPANDED_WIDTH = 220;
    private static final double COLLAPSED_WIDTH = 26;

    private final BooleanProperty expanded = new SimpleBooleanProperty(false);

    public SideNav() {
        setPrefWidth(COLLAPSED_WIDTH);

        Button toggle = new Button("≡");
        toggle.setOnAction(e -> toggle());

        getChildren().addAll(toggle);
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

    }
}
