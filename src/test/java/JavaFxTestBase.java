import javafx.application.Platform;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

abstract class JavaFxTestBase {

    private static volatile boolean toolkitReady;

    @BeforeAll
    static void initToolkit() {
        if (toolkitReady) {
            return;
        }
        try {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            latch.await(15, TimeUnit.SECONDS);
            Platform.setImplicitExit(false);
            toolkitReady = true;
        } catch (IllegalStateException alreadyStarted) {
            // The JavaFX toolkit was already initialized by another test class.
            toolkitReady = true;
        } catch (Throwable headlessOrUnavailable) {
            // No display / headless CI without Monocle: tests guard on this and skip.
            toolkitReady = false;
        }
    }

    static void assumeToolkit() {
        Assumptions.assumeTrue(toolkitReady, "JavaFX toolkit is unavailable (headless environment)");
    }

    static <T> T onFx(Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return action.call();
        }
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(action.call());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(20, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for the JavaFX thread");
        }
        if (failure.get() != null) {
            throw new Exception(failure.get());
        }
        return result.get();
    }

    static void runFx(RunnableFx action) throws Exception {
        onFx(() -> {
            action.run();
            return null;
        });
    }

    @FunctionalInterface
    interface RunnableFx {
        void run() throws Exception;
    }
}

