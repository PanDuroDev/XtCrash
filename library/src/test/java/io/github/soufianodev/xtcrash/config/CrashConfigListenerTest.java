package io.github.soufianodev.xtcrash.config;

import io.github.soufianodev.xtcrash.CrashActivity;
import io.github.soufianodev.xtcrash.config.fixtures.ForeignBases;
import io.github.soufianodev.xtcrash.log.CrashLogListener;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CrashConfigListenerTest {
    @Test
    public void acceptsPublicConstructorAndIgnoresNonSerializableAncestorFields() throws Exception {
        assertAccepted(new PublicListener());
    }

    @Test
    public void acceptsProtectedConstructorAcrossPackages() throws Exception {
        assertAccepted(new ProtectedListener());
    }

    @Test
    public void acceptsPackageConstructorInSamePackage() throws Exception {
        assertAccepted(new PackageListener());
    }

    @Test
    public void doesNotRequireListenerItselfToHaveNoArgumentConstructor() throws Exception {
        assertAccepted(new ParameterizedListener(1));
    }

    @Test
    public void rejectsMissingNoArgumentConstructor() throws Exception {
        assertRejected(new MissingConstructorListener(), MissingConstructorBase.class);
    }

    @Test
    public void checksBeyondSerializableSuperclasses() throws Exception {
        assertRejected(new InheritedListener(), MissingConstructorBase.class);
    }

    @Test
    public void rejectsPrivateConstructorEvenForNestmates() throws Exception {
        assertRejected(new PrivateListener(), PrivateBase.class);
    }

    @Test
    public void rejectsPackageConstructorAcrossPackages() throws Exception {
        assertRejected(new ForeignPackageListener(), ForeignBases.PackageBase.class);
    }

    private static void assertAccepted(TestListener listener) throws Exception {
        CrashConfig config = new CrashConfig.Builder()
                .eventListener(listener).logListener(listener).get();
        assertSame(listener, config.getEventListener());
        assertSame(listener, config.getLogListener());
        CrashConfig restored = (CrashConfig) roundTrip(config);
        assertEquals(listener.getClass(), restored.getEventListener().getClass());
        assertSame(restored.getEventListener(), restored.getLogListener());
    }

    private static void assertRejected(TestListener listener, Class<?> ancestor) throws Exception {
        // Confirm the rejected hierarchy actually fails Java deserialization.
        assertThrows(InvalidClassException.class, () -> roundTrip(listener));
        CrashConfig.Builder builder = new CrashConfig.Builder();
        IllegalArgumentException eventError = assertThrows(IllegalArgumentException.class,
                () -> builder.eventListener(listener));
        IllegalArgumentException logError = assertThrows(IllegalArgumentException.class,
                () -> builder.logListener(listener));
        assertTrue(eventError.getMessage().contains("event listener"));
        assertTrue(logError.getMessage().contains("log listener"));
        for (IllegalArgumentException error : new IllegalArgumentException[]{eventError, logError}) {
            assertTrue(error.getMessage().contains(ancestor.getName()));
            assertTrue(error.getMessage().contains("accessible no-argument constructor"));
        }
    }

    private static Object roundTrip(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return input.readObject();
        }
    }

    public interface TestListener extends CrashActivity.EventListener, CrashLogListener {
        @Override default void onLaunchErrorActivity() {}
        @Override default void onRestartAppFromErrorActivity() {}
        @Override default void onCloseAppFromErrorActivity() {}
        @Override default void onCrashLog(int priority, String tag, String message, Throwable error) {}
    }

    public static class PublicBase {
        final Object state = new Object();
        public PublicBase() {}
    }

    public static class PublicListener extends PublicBase implements TestListener {}

    public static class ProtectedListener extends ForeignBases.ProtectedBase
            implements TestListener {}

    public static class PackageBase {
        PackageBase() {}
    }

    public static class PackageListener extends PackageBase implements TestListener {}

    public static class ParameterizedListener implements TestListener {
        public ParameterizedListener(int ignored) {}
    }

    public static class MissingConstructorBase {
        public MissingConstructorBase(int ignored) {}
    }

    public static class MissingConstructorListener extends MissingConstructorBase
            implements TestListener {
        public MissingConstructorListener() { super(1); }
    }

    public static class InheritedListener extends MissingConstructorListener {}

    public static class PrivateBase {
        private PrivateBase() {}
    }

    public static class PrivateListener extends PrivateBase implements TestListener {}

    public static class ForeignPackageListener extends ForeignBases.PackageBase
            implements TestListener {
        public ForeignPackageListener() { super(1); }
    }
}
