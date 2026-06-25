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
        ui.setDiagnosticsLog(controller.diagnostics());
        ui.setOperationAudit(controller.operationAudit());
        ui.setOnPsmpTabSelected(controller::loadPSMPServersIfNeeded);
        ui.setOnPsmTabSelected(controller::loadPSMServersIfNeeded);
        ui.setOnConnectionComponentTabSelected(controller::loadConnectionComponentIfNeeded);
        ui.setOnConnectionComponentRowDoubleClicked(controller::showConnectionComponentDetails);
        ui.setOnConnectionComponentSelected(controller::onConnectionComponentSelected);
        ui.setOnConnectionComponentExport(controller::exportConnectionComponents);
        ui.setOnOpenOutputFolder(controller::openOutputFolder);
        ui.setOnOpenExportsFolder(controller::openExportsFolder);
        ui.setOnConnectionComponentRemove(controller::removeConnectionComponents);
        ui.setOnConnectionComponentUnlink(controller::unlinkConnectionComponents);
        ui.setOnOrderComponents(controller::orderConnectionComponents);
        ui.setOnFindOrphanComponents(controller::findOrphanComponentReferences);
        ui.setOnPopulateEmptyPolicies(controller::populateEmptyPolicies);
        ui.setOnOrphanRemoveReference(controller::removeOrphanReference);
        ui.setOnOrphanRemoveComponent(controller::removeOrphanComponentEverywhere);
        ui.setOnOrphanRemoveAll(controller::removeAllOrphans);
        ui.setOnImportPsmComponent(controller::importPsmComponents);
        ui.setOnPvwaConnect(controller::connectToPvwa);
        ui.setOnPvwaDisconnect(controller::disconnectFromPvwa);
        ui.setOnImportFromFileOnline(controller::importPsmComponentsOnline);
        ui.setOnConnectionComponentImportOnline(controller::importSelectedComponentsOnline);
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
        ui.setCompareItemLoader(controller::loadCompareItems);
        ui.setCompareRunner(controller::runCompare);
        ui.setOnAppClose(controller::shutdown);
        ui.setActiveOperationCheck(controller::hasActiveOperations);
        ui.setupUI(stage);
        controller.loadAll();
    }
}