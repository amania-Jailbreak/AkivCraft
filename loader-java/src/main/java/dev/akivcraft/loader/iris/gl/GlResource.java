package dev.akivcraft.loader.iris.gl;

// Derived from Iris (LGPL-3.0), adapted for AkivCraft.
public abstract class GlResource {
    private final int id;
    private boolean valid;

    protected GlResource(int id) {
        this.id = id;
        this.valid = true;
    }

    public final void destroy() {
        if (!valid) return;
        destroyInternal();
        valid = false;
    }

    protected abstract void destroyInternal();

    protected final void assertValid() {
        if (!valid) throw new IllegalStateException("Tried to use a destroyed GlResource");
    }

    protected final int getGlId() {
        assertValid();
        return id;
    }
}
