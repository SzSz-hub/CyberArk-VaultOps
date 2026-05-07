import javafx.application.Application;
import javafx.stage.Stage;

public class Main extends Application {
    private static final UI ui = new UI();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        AppController controller = new AppController(ui);
        ui.setOnPsmpTabSelected(controller::loadPSMPServersIfNeeded);
        ui.setOnPsmTabSelected(controller::loadPSMServersIfNeeded);
        ui.setOnConnectionComponentTabSelected(controller::loadConnectionComponentIfNeeded);
        ui.setOnConnectionComponentRowSelected(controller::showConnectionComponentDetails);
        ui.setupUI(stage);
        controller.loadAll();
    }
}
