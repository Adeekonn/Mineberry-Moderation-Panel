package com.adikan;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ClientCommandHandler {
    private static final class QueuedCommand {
        private final String command;
        private final int dueTick;

        private QueuedCommand(String command, int dueTick) {
            this.command = command;
            this.dueTick = dueTick;
        }
    }

    private static final KeyMapping DOT_CHAT_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.mineberrybanmod.open_chat_dot",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PERIOD,
            KeyMapping.Category.MISC
    ));
    private static final List<QueuedCommand> COMMAND_QUEUE = new ArrayList<>();
    private static int clientTick = 0;

    public static void register() {
        ChatDataListener.register();

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (message.startsWith(".m ")) {
                String[] parts = message.split(" ", 2);
                if (parts.length == 2) {
                    String username = parts[1].trim();
                    Minecraft client = Minecraft.getInstance();
                    client.execute(() -> client.setScreen(new ModerationScreen(username)));
                }
                return false;
            }

            return true;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            clientTick++;
            if (client == null || client.player == null) {
                return;
            }

            flushQueuedCommands(client);

            while (DOT_CHAT_KEY.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new ChatScreen(".", false));
                }
            }
        });
    }

    public static void enqueueCommands(List<String> commands, int ticksBetweenCommands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        int safeGap = Math.max(0, ticksBetweenCommands);
        int due = clientTick;
        for (String command : commands) {
            if (command == null || command.isBlank()) {
                continue;
            }
            COMMAND_QUEUE.add(new QueuedCommand(command, due));
            due += safeGap;
        }
    }

    private static void flushQueuedCommands(Minecraft client) {
        if (COMMAND_QUEUE.isEmpty()) {
            return;
        }
        for (int i = 0; i < COMMAND_QUEUE.size(); i++) {
            QueuedCommand queued = COMMAND_QUEUE.get(i);
            if (queued.dueTick <= clientTick) {
                client.player.connection.sendCommand(queued.command);
                COMMAND_QUEUE.remove(i);
                i--;
            }
        }
    }
}
