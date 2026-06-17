import javafx.application.Application;
import javafx.stage.Stage;

public class Main extends Application {
    private static final AppSettingsStore settingsStore = new AppSettingsStore();
    private static final AppSettings settings = settingsStore.load();
    private static final UI ui = new UI(settings, settingsStore);

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        AppController controller = new AppController(ui, settings, ui::showToast);
        ui.setOnPsmpTabSelected(controller::loadPSMPServersIfNeeded);
        ui.setOnPsmTabSelected(controller::loadPSMServersIfNeeded);
        ui.setOnConnectionComponentTabSelected(controller::loadConnectionComponentIfNeeded);
        ui.setOnConnectionComponentRowSelected(controller::showConnectionComponentDetails);
        ui.setOnConnectionComponentSelected(controller::onConnectionComponentSelected);
        ui.setOnPoliciesTabSelected(controller::loadPoliciesIfNeeded);
        ui.setOnUsagesTabSelected(controller::loadUsageIfNeeded);
        ui.setOnTargetsTabSelected(controller::loadTargetsIfNeeded);
        ui.setOnPolicyRowSelected(controller::onPolicySelected);
        ui.setOnPolicyRowDoubleClicked(controller::showPolicyDetails);
        ui.setOnUsageRowSelected(controller::onUsageSelected);
        ui.setOnSourceProfileChanged(controller::onSourceProfileChanged);
        ui.setOnRefreshCurrentRequested(controller::invalidateCurrentSelection);
        ui.setOnReloadAllRequested(controller::invalidateAllSourcesForActiveEnvironment);
        ui.setOnStatusRefreshRequested(controller::refreshStatusIndicators);
        ui.setupUI(stage);
        controller.loadAll();
    }
}