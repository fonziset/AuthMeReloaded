package fr.xephi.authme;

import fr.xephi.authme.command.CommandService;
import fr.xephi.authme.util.BukkitService;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.verify;

/**
 * AuthMe test utilities.
 */
public final class TestHelper {

    private TestHelper() {
    }

    /**
     * Return a {@link File} to a file in the JAR's resources (main or test).
     *
     * @param path The absolute path to the file
     * @return The project file
     */
    public static File getJarFile(String path) {
        URL url = getUrlOrThrow(path);
        return new File(url.getFile());
    }

    /**
     * Return a {@link Path} to a file in the JAR's resources (main or test).
     *
     * @param path The absolute path to the file
     * @return The Path object to the file
     */
    public static Path getJarPath(String path) {
        String sqlFilePath = getUrlOrThrow(path).getPath();
        // Windows preprends the path with a '/' or '\', which Paths cannot handle
        String appropriatePath = System.getProperty("os.name").contains("indow")
            ? sqlFilePath.substring(1)
            : sqlFilePath;
        return Paths.get(appropriatePath);
    }

    private static URL getUrlOrThrow(String path) {
        URL url = TestHelper.class.getResource(path);
        if (url == null) {
            throw new IllegalStateException("File '" + path + "' could not be loaded");
        }
        return url;
    }

    /**
     * Execute a {@link Runnable} passed to a mock's {@link CommandService#runTaskAsynchronously} method.
     * Note that calling this method expects that there be a runnable sent to the method and will fail
     * otherwise.
     *
     * @param service The mock service
     */
    public static void runInnerRunnable(CommandService service) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(service).runTaskAsynchronously(captor.capture());
        Runnable runnable = captor.getValue();
        runnable.run();
    }

    /**
     * Execute a {@link Runnable} passed to a mock's {@link BukkitService#scheduleSyncDelayedTask(Runnable, long)}
     * method. Note that calling this method expects that there be a runnable sent to the method and will fail
     * otherwise.
     *
     * @param service The mock service
     */
    public static void runSyncDelayedTaskWithDelay(BukkitService service) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(service).scheduleSyncDelayedTask(captor.capture(), anyLong());
        Runnable runnable = captor.getValue();
        runnable.run();
    }

    /**
     * Assign the necessary fields on ConsoleLogger with mocks.
     *
     * @return The logger mock used
     */
    public static Logger setupLogger() {
        Logger logger = Mockito.mock(Logger.class);
        ConsoleLogger.setLogger(logger);
        return logger;
    }

    /**
     * Check that a class only has a hidden, zero-argument constructor, preventing the
     * instantiation of such classes (utility classes). Invokes the hidden constructor
     * as to register the code coverage.
     *
     * @param clazz The class to validate
     */
    public static void validateHasOnlyPrivateEmptyConstructor(Class<?> clazz) {
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();
        if (constructors.length > 1) {
            throw new IllegalStateException("Class " + clazz.getSimpleName() + " has more than one constructor");
        } else if (constructors[0].getParameterTypes().length != 0) {
            throw new IllegalStateException("Constructor of " + clazz + " does not have empty parameter list");
        } else if (!Modifier.isPrivate(constructors[0].getModifiers())) {
            throw new IllegalStateException("Constructor of " + clazz + " is not private");
        }

        // Ugly hack to get coverage on the private constructors
        // http://stackoverflow.com/questions/14077842/how-to-test-a-private-constructor-in-java-application
        try {
            constructors[0].setAccessible(true);
            constructors[0].newInstance();
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new UnsupportedOperationException(e);
        }
    }

}
