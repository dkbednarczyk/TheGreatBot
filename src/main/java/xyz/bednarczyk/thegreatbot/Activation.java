package xyz.bednarczyk.thegreatbot;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class Activation {
    private static final long CODE_EXPIRY_MINUTES = 30;
    private static final Logger LOGGER = LoggerFactory.getLogger(TheGreatBot.MOD_ID);
    private static final Map<String, Activation> pendingActivations = new ConcurrentHashMap<>();

    private final UUID playerUUID;
    private final String playerName;
    private final long timestamp;

    public Activation(ServerPlayerEntity player) {
        this.playerUUID = player.getUuid();
        this.playerName = player.getStringifiedName();
        this.timestamp = System.currentTimeMillis();
    }

    public static void startActivationSequence(ServerPlayerEntity player) {
        UUID playerUUID = player.getUuid();

        // Find existing activation for this player
        for (var entry : pendingActivations.entrySet()) {
            Activation existing = entry.getValue();
            if (existing.playerUUID.equals(playerUUID)) {
                if (!existing.isExpired()) {
                    // Reuse existing valid code
                    kickWithMessage(player, entry.getKey());
                    return;
                } else {
                    // Remove expired code
                    pendingActivations.remove(entry.getKey());
                    break;
                }
            }
        }

        // Generate new secure code
        SecureRandom random = new SecureRandom();
        String code;
        int attempts = 0;
        do {
            code = new DecimalFormat("000000").format(random.nextInt(1000000));
            attempts++;
            if (attempts > 100) {
                LOGGER.error("Failed to generate unique activation code after 100 attempts");
                player.networkHandler.disconnect(Text.literal("Failed to generate activation code. Please try again."));
                return;
            }
        } while (pendingActivations.putIfAbsent(code, new Activation(player)) != null);

        LOGGER.info("Generated activation code for player: {}", player.getStringifiedName());
        kickWithMessage(player, code);
    }

    static Activation get(String code) {
        return pendingActivations.get(code);
    }

    static void remove(String code) {
        pendingActivations.remove(code);
    }


    static void cleanupExpired() {
        pendingActivations.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    static int pendingCount() {
        return pendingActivations.size();
    }

    private static void kickWithMessage(@NotNull ServerPlayerEntity player, String code) {
        Text kickMessage = Text.literal("Your account is not activated.\n").styled(style -> style.withColor(Formatting.RED))
                .append(Text.literal("This is usually because you are either joining for the first time or your username has changed.\n").styled(style -> style.withColor(Formatting.GRAY)))
                .append(Text.literal("Your temporary activation code is: " + code + "\n").styled(style -> style.withColor(Formatting.YELLOW)))
                .append(Text.literal("Give this code to the member who invited you.\n\n").styled(style -> style.withColor(Formatting.GRAY)))
                .append(Text.literal("This code will expire in " + CODE_EXPIRY_MINUTES + " minutes.").styled(style -> style.withColor(Formatting.BLUE)));

        if (player.networkHandler == null) {
            LOGGER.warn("Player {} network handler is null, cannot kick with activation message", player.getStringifiedName());
            return;
        }

        player.networkHandler.disconnect(kickMessage);
        player.remove(Entity.RemovalReason.DISCARDED);
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - timestamp > TimeUnit.MINUTES.toMillis(CODE_EXPIRY_MINUTES);
    }

    public UUID getPlayerUUID() {
        return this.playerUUID;
    }

    public String getPlayerName() {
        return this.playerName;
    }
}