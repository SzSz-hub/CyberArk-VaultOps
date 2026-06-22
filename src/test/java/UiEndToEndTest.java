import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("UI end-to-end (real JavaFX views + controller + parsers)")
class UiEndToEndTest extends JavaFxTestBase {

    private AppController buildWiredApp(Path settingsFile, String fixtureFolder) {
        AppSettingsStore store = new AppSettingsStore(settingsFile);
        AppSettings settings = new AppSettings();
        settings.replaceSourceProfiles(List.of(
                new AppSettings.SourceProfile("test-src", "Test Source", "TS", fixtureFolder)));
        settings.setActiveProfileId("test-src");

        UI ui = new UI(settings, store);
        AppController controller = new AppController(ui, settings, message -> { });
        // reloadCurrentTab() runs this callback when the (default) Connection Components tab is shown.
        ui.setOnConnectionComponentTabSelected(controller::loadConnectionComponentIfNeeded);
        return controller;
    }

    @Test
    @DisplayName("setupUI + tab refresh builds the table and the controller loads fixtures into it")
    void loadsConnectionComponentsIntoTable(@TempDir Path tmp) throws Exception {
        assumeToolkit();

        String fixtureFolder = TestSupport.fixture("PVConfiguration.xml").getParent().toString();
        Path settingsFile = tmp.resolve("app.properties");

        AppController[] holder = new AppController[1];
        UI[] uiHolder = new UI[1];
        try {
            runFx(() -> {
                AppController controller = buildWiredApp(settingsFile, fixtureFolder);
                holder[0] = controller;
                UI ui = uiOf(controller);
                uiHolder[0] = ui;

                ui.setupUI(new Stage());
                // Selected tab is Connection Components; this builds its content/table and fires the load.
                ui.refreshCurrentTabContent();

                assertNotNull(ui.getConnectionComponentTable(),
                        "Connection components table should exist after the tab is built");
            });

            int size = waitForItemCount(uiHolder[0], 5, 8_000);
            assertTrue(size == 5, "Expected 5 components loaded from fixtures, got " + size);
        } finally {
            if (holder[0] != null) {
                holder[0].shutdown();
            }
        }
    }

    @Test
    @DisplayName("setupUI builds the primary view and selects the Connection Components tab")
    void setupBuildsPrimaryView(@TempDir Path tmp) throws Exception {
        assumeToolkit();

        String fixtureFolder = TestSupport.fixture("Policies.xml").getParent().toString();
        Path settingsFile = tmp.resolve("app.properties");

        AppController[] holder = new AppController[1];
        try {
            runFx(() -> {
                AppController controller = buildWiredApp(settingsFile, fixtureFolder);
                holder[0] = controller;
                UI ui = uiOf(controller);
                ui.setOnPoliciesTabSelected(controller::loadPoliciesIfNeeded);

                ui.setupUI(new Stage());
                ui.refreshCurrentTabContent();

                assertNotNull(ui.getConnectionComponentTable());
                assertTrue(UI.TAB_CONNECTION_COMPONENTS.equals(ui.getSelectedTabName()));
            });
        } finally {
            if (holder[0] != null) {
                holder[0].shutdown();
            }
        }
    }

    // The controller does not expose its UI; recover it reflectively to keep the test self-contained.
    private static UI uiOf(AppController controller) {
        try {
            var field = AppController.class.getDeclaredField("ui");
            field.setAccessible(true);
            return (UI) field.get(controller);
        } catch (Exception e) {
            return fail("Could not access AppController.ui: " + e.getMessage());
        }
    }

    private int waitForItemCount(UI ui, int expected, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        int size = -1;
        while (System.currentTimeMillis() < deadline) {
            size = onFx(() -> {
                TableView<?> table = ui.getConnectionComponentTable();
                return table == null ? -1 : table.getItems().size();
            });
            if (size == expected) {
                return size;
            }
            Thread.sleep(100);
        }
        return size;
    }
}


