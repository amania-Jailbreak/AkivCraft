package dev.akivcraft.loader.iris.mixinterface;

// Derived from Iris (LGPL-3.0), adapted for AkivCraft.
public interface RenderPassInterface {
    default void iris$setCustomPass(CustomPass pass) {
        throw new UnsupportedOperationException();
    }

    default CustomPass iris$getCustomPass() {
        throw new UnsupportedOperationException();
    }
}
