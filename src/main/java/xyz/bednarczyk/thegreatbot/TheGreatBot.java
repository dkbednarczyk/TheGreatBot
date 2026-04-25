package xyz.bednarczyk.thegreatbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TheGreatBot implements ModInitializer {
    public static final String MOD_ID = "thegreatbot";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private volatile MinecraftServer server;
    private JDA jda;
    private ScheduledExecutorService cleanupExecutor;
    private ActivationService activationService;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing TheGreatBot...");

        Config config = Config.load();
        if (config == null) {
            LOGGER.error("Failed to load The Great Bot config file. Mod will not function.");
            return;
        }

        activationService = new ActivationService();

        try {
            jda = JDABuilder
                    .createLight(config.botToken, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES)
                    .addEventListeners(new ActivationListener(config, activationService, () -> server))
                    .build();

            LOGGER.info("Discord bot initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize Discord bot: {}", e.getMessage(), e);
            return;
        }

        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "TheGreatBot-Cleanup");
            thread.setDaemon(true);
            return thread;
        });

        cleanupExecutor.scheduleAtFixedRate(() -> {
            try {
                int before = activationService.pendingCount();
                activationService.cleanupExpired();

                int removed = before - activationService.pendingCount();
                if (removed > 0) {
                    LOGGER.info("Cleaned up {} expired activation codes", removed);
                }
            } catch (Exception e) {
                LOGGER.error("Error during activation code cleanup", e);
            }
        }, 5, 5, TimeUnit.MINUTES);

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            this.server = server;
            LOGGER.info("Minecraft server starting, TheGreatBot is ready");
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(_ -> {
            LOGGER.info("Server stopping, shutting down TheGreatBot...");
            this.server = null;
            if (jda != null) {
                jda.shutdown();
            }
            if (cleanupExecutor != null) {
                cleanupExecutor.shutdown();
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, _, server) -> {
            ServerPlayer player = handler.getPlayer();
            String playerName = player.getName().getString();

            try {
                boolean isActivated = activationService.handlePlayerJoin(server, player);
                if (!isActivated) {
                    return;
                }

                player.sendSystemMessage(
                        Component.literal("Welcome back, " + playerName + "!")
                                .withStyle(ChatFormatting.GREEN)
                );
            } catch (Exception e) {
                LOGGER.error("Error handling player join for {}", playerName, e);
            }
        });


        LOGGER.info("TheGreatBot initialization complete");
    }
}
