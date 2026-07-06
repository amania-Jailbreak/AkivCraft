package dev.akivcraft.loader;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class ClientEventBus {
    private final List<Consumer<String>> chatListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> keyPressListeners = new CopyOnWriteArrayList<>();

    public void onChatMessage(Consumer<String> listener) {
        chatListeners.add(listener);
    }

    public void onKeyPress(Consumer<String> listener) {
        keyPressListeners.add(listener);
    }

    public void publishChatMessage(String message) {
        chatListeners.forEach(listener -> listener.accept(message));
    }

    public void publishKeyPress(String key) {
        keyPressListeners.forEach(listener -> listener.accept(key));
    }
}
