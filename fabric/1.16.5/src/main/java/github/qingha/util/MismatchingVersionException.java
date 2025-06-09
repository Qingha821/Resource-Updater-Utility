package github.qingha.util;

public class MismatchingVersionException extends Exception {

    public MismatchingVersionException(String requested, String current) {
        super("\n\n" +
                String.format("Please update your Resource Pack Updater mod to the version %s (You are now using %s)", requested, current) +
                "\n\n"
        );
    }
    public MismatchingVersionException(String message) {
        super(message);
    }
}
