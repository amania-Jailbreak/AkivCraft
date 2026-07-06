package dev.akivcraft.loader;

public final class AkivCraftStartupException extends RuntimeException {
    public AkivCraftStartupException(String message) {
        super(message);
    }

    public AkivCraftStartupException(String message, Throwable cause) {
        super(message, cause);
    }
}
