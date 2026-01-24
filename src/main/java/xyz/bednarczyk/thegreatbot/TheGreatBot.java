package xyz.bednarczyk.thegreatbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TheGreatBot implements ModInitializer {
    public static final String MOD_ID = "thegreatbot";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Config CONFIG;
    public static volatile MinecraftServer SERVER;
    public static JDA jda;
    private static ScheduledExecutorService cleanupExecutor;


    @Override
    public void onInitialize() {
        LOGGER.info("Initializing TheGreatBot...");

        CONFIG = Config.load();
        if (CONFIG == null) {
            LOGGER.error("Failed to load The Great Bot config file. Mod will not function.");
            return;
        }

        try {
            jda = JDABuilder
                    .createLight(CONFIG.botToken, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES)
                    .addEventListeners(new ActivationListener())
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
                int before = Activation.pendingCount();
                Activation.cleanupExpired();

                int removed = before - Activation.pendingCount();
                if (removed > 0) {
                    LOGGER.info("Cleaned up {} expired activation codes", removed);
                }
            } catch (Exception e) {
                LOGGER.error("Error during activation code cleanup", e);
            }
        }, 5, 5, TimeUnit.MINUTES);

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            SERVER = server;
            LOGGER.info("Minecraft server starting, TheGreatBot is ready");
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("Server stopping, shutting down TheGreatBot...");
            if (jda != null) {
                jda.shutdown();
            }
            if (cleanupExecutor != null) {
                cleanupExecutor.shutdown();
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            String playerName = player.getStringifiedName();
            UUID playerUUID = player.getUuid();

            // Null check for server
            if (SERVER == null) {
                LOGGER.error("Server instance is null during player join!");
                return;
            }

            try {
                ActivationState state = ActivationState.getServerState(SERVER);

                var isActivated = state.isActivated(playerUUID);
                if (!isActivated) {
                    LOGGER.info("Player {} is not activated, starting activation sequence", playerName);
                    Activation.startActivationSequence(player);
                    return;
                }

                player.sendMessage(
                        Text.literal("Welcome back, " + playerName + "!")
                                .styled(style -> style.withColor(Formatting.GREEN))
                );
            } catch (Exception e) {
                LOGGER.error("Error handling player join for {}", playerName, e);
            }
        });


        LOGGER.info("TheGreatBot initialization complete");
    }
}
