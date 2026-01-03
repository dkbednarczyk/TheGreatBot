package xyz.bednarczyk.thegreatbot;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class Activation {
    private static final long CODE_EXPIRY_MINUTES = 30;
    private static final Logger LOGGER = LoggerFactory.getLogger(TheGreatBot.MOD_ID);
    public static final Map<String, Activation> pendingActivations = new ConcurrentHashMap<>();

    public final String playerName;
    private final long timestamp;

    public Activation(String playerName) {
        this.playerName = playerName;
        this.timestamp = System.currentTimeMillis();
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - timestamp > TimeUnit.MINUTES.toMillis(CODE_EXPIRY_MINUTES);
    }

    public static void startActivationSequence(ServerPlayerEntity player, String playerName) {
        String existingCode = findCodeForPlayer(playerName);

        if (existingCode != null) {
            Activation existing = pendingActivations.get(existingCode);
            if (existing != null && !existing.isExpired()) {
                sendActivationMessages(player, existingCode);
                return;
            } else {
                // Remove expired code
                pendingActivations.remove(existingCode);
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
        } while (pendingActivations.putIfAbsent(code, new Activation(playerName)) != null);

        LOGGER.info("Generated activation code for player: {}", playerName);
        sendActivationMessages(player, code);
    }

    public static String findCodeForPlayer(String playerName) {
        for (Map.Entry<String, Activation> entry : pendingActivations.entrySet()) {
            if (entry.getValue().playerName.equalsIgnoreCase(playerName)) {
                return entry.getKey();
            }
        }
        return null;
    }


    private static void sendActivationMessages(ServerPlayerEntity player, String code) {
        player.changeGameMode(GameMode.ADVENTURE);

        player.sendMessage(
                Text.literal("This server is invite only, and your account is not activated.")
                        .styled(style -> style.withColor(Formatting.RED))
        );

        player.sendMessage(
                Text.literal("Your temporary activation code is: " + code + ". Give this code to the member who invited you.")
                        .styled(style -> style.withColor(Formatting.YELLOW))
        );

        player.sendMessage(
                Text.literal("This code will expire in " + CODE_EXPIRY_MINUTES + " minutes.")
                        .styled(style -> style.withColor(Formatting.GRAY))
        );
    }
}