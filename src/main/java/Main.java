import javafx.application.Application;
import javafx.stage.Stage;

public class Main extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        AppSettingsStore settingsStore = new AppSettingsStore();
        AppSettings settings = settingsStore.load();
        UI ui = new UI(settings, settingsStore);
        settingsStore.setErrorHandler(ui::showToast);

        AppController controller = new AppController(ui, settings, ui::showToast);
        ui.setOnPsmpTabSelected(controller::loadPSMPServersIfNeeded);
        ui.setOnPsmTabSelected(controller::loadPSMServersIfNeeded);
        ui.setOnConnectionComponentTabSelected(controller::loadConnectionComponentIfNeeded);
        ui.setOnConnectionComponentRowDoubleClicked(controller::showConnectionComponentDetails);
        ui.setOnConnectionComponentSelected(controller::onConnectionComponentSelected);
        ui.setOnPoliciesTabSelected(controller::loadPoliciesIfNeeded);
        ui.setOnUsagesTabSelected(controller::loadUsageIfNeeded);
        ui.setOnTargetsTabSelected(controller::loadTargetsIfNeeded);
        ui.setOnAlteredAddressSelected(controller::onAlteredAddressSelected);
        ui.setOnPolicyRowSelected(controller::onPolicySelected);
        ui.setOnPolicyRowDoubleClicked(controller::showPolicyDetails);
        ui.setOnUsageRowSelected(controller::onUsageSelected);
        ui.setOnUsageRowDoubleClicked(controller::showUsageDetails);
        ui.setOnConnectionAssignmentDoubleClicked(controller::showConnectionAssignmentDetails);
        ui.setOnPolicyComponentDoubleClicked(controller::showPolicyComponentDetails);
        ui.setOnUsagePolicyDoubleClicked(controller::showUsagePolicyDetails);
        ui.setOnSourceProfileChanged(controller::onSourceProfileChanged);
        ui.setOnRefreshCurrentRequested(controller::invalidateCurrentSelection);
        ui.setOnReloadAllRequested(controller::invalidateAllSourcesForActiveEnvironment);
        ui.setOnStatusRefreshRequested(controller::refreshStatusIndicators);
        ui.setOnAppClose(controller::shutdown);
        ui.setupUI(stage);
        controller.loadAll();
    }
}