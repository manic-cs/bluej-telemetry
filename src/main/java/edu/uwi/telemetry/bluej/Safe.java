package edu.uwi.telemetry.bluej;

/**
 * Guard that runs listener link so that nothing can escape into BlueJ.
 * Every handler in this package wraps its body in {@link Safe.run(...)}, which catches any
 * {@link Exception} or {@link LinkageError} and prints one line to the BlueJ log instead of throwing.
 * Telemetry must never disturb the student's work in BlueJ. The class is package-private and holds
 * only a static method, so its constructor is private and never called.
 * @author Rinard Guy
 */

final class Safe {
    private Safe() {
    }

    static void run(Runnable action) {
        try {
            action.run();
        }

        catch (Exception | LinkageError ex) {
            System.err.println("[BlueJ Telemetry] ignored error: " + ex);
        }
    }
}