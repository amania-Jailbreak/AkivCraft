package dev.akivcraft.loader.iris.targets;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.HashSet;
import java.util.Set;

// Derived from Iris (LGPL-3.0), adapted for AkivCraft.
public class BufferFlipper {
    private final IntSet flippedBuffers = new IntOpenHashSet();

    public void flip(int target) {
        if (!flippedBuffers.remove(target)) flippedBuffers.add(target);
    }

    public boolean isFlipped(int target) {
        return flippedBuffers.contains(target);
    }

    public IntIterator getFlippedBuffers() {
        return flippedBuffers.iterator();
    }

    public Set<Integer> snapshot() {
        Set<Integer> copy = new HashSet<>();
        IntIterator iterator = flippedBuffers.iterator();
        while (iterator.hasNext()) copy.add(iterator.nextInt());
        return Set.copyOf(copy);
    }
}
