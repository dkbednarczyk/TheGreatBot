package xyz.bednarczyk.thegreatbot;

import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActivationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        TheGreatBot.MOD_ID
    );

    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, Activation> pendingByPlayer =
        new ConcurrentHashMap<>();
    private final Map<String, UUID> codeToPlayer = new ConcurrentHashMap<>();

    public boolean handlePlayerJoin(
        MinecraftServer server,
        ServerPlayer player
    ) {
        ActivationState state = ActivationState.getServerState(server);
        if (state.isActivated(player.getUUID())) {
            return true;
        }

        try {
            Activation activation = getOrCreatePendingActivation(player);

            LOGGER.info(
                "Player {} is not activated, sending activation code",
                player.getName().getString()
            );

            kickWithMessage(player, activation.getCode());
        } catch (IllegalStateException e) {
            LOGGER.error(
                "Failed to create activation code for player {}",
                player.getName().getString(),
                e
            );

            disconnectWithError(player);
        }

        return false;
    }

    public ApprovalResult approveActivation(String code) {
        Activation activation = getPendingActivation(code);
        if (activation == null) {
            return ApprovalResult.invalid();
        }

        if (activation.isExpired()) {
            removePendingActivation(activation);

            LOGGER.info(
                "Expired activation code used: {} for player {}",
                code,
                activation.getPlayerName()
            );

            return ApprovalResult.expired();
        }

        return ApprovalResult.success(removePendingActivation(activation));
    }

    public void cleanupExpired() {
        for (Activation activation : pendingByPlayer.values()) {
            if (activation.isExpired()) {
                removePendingActivation(activation);
            }
        }
    }

    public int pendingCount() {
        return pendingByPlayer.size();
    }

    private Activation getOrCreatePendingActivation(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        Activation existing = pendingByPlayer.get(playerUUID);

        if (existing != null && !existing.isExpired()) {
            return existing;
        }

        if (existing != null) {
            removePendingActivation(existing);
        }

        for (int attempts = 0; attempts < 100; attempts++) {
            String code = new DecimalFormat("000000").format(
                random.nextInt(1_000_000)
            );

            Activation activation = new Activation(code, player);
            UUID previous = codeToPlayer.putIfAbsent(code, playerUUID);
            if (previous != null) {
                continue;
            }

            pendingByPlayer.put(playerUUID, activation);
            LOGGER.info(
                "Generated activation code for player: {}",
                activation.getPlayerName()
            );

            return activation;
        }

        throw new IllegalStateException(
            "Failed to generate unique activation code after 100 attempts"
        );
    }

    private Activation getPendingActivation(String code) {
        UUID playerUUID = codeToPlayer.get(code);
        if (playerUUID == null) {
            return null;
        }

        Activation activation = pendingByPlayer.get(playerUUID);
        if (activation == null) {
            codeToPlayer.remove(code, playerUUID);
            return null;
        }

        if (!activation.getCode().equals(code)) {
            codeToPlayer.remove(code, playerUUID);
            return null;
        }

        return activation;
    }

    private Activation removePendingActivation(Activation activation) {
        pendingByPlayer.remove(activation.getPlayerUUID(), activation);
        codeToPlayer.remove(activation.getCode(), activation.getPlayerUUID());

        return activation;
    }

    private static void kickWithMessage(
        @NotNull ServerPlayer player,
        String code
    ) {
        Component kickMessage = Component.literal(
            "Your account is not activated.\n"
        )
            .withStyle(ChatFormatting.RED)
            .append(
                Component.literal(
                    "This is usually because you are either joining for the first time or your username has changed.\n"
                ).withStyle(ChatFormatting.GRAY)
            )
            .append(
                Component.literal(
                    "Your temporary activation code is: " + code + "\n"
                ).withStyle(ChatFormatting.YELLOW)
            )
            .append(
                Component.literal(
                    "Give this code to the member who invited you.\n\n"
                ).withStyle(ChatFormatting.GRAY)
            )
            .append(
                Component.literal(
                    "This code will expire in " +
                        Activation.CODE_EXPIRY_MINUTES +
                        " minutes."
                ).withStyle(ChatFormatting.BLUE)
            );

        player.connection.disconnect(kickMessage);
    }

    private static void disconnectWithError(@NotNull ServerPlayer player) {
        player.connection.disconnect(
            Component.literal(
                "Failed to generate activation code. Please try again."
            )
        );
    }

    public record ApprovalResult(Status status, Activation activation) {
        public static ApprovalResult invalid() {
            return new ApprovalResult(Status.INVALID, null);
        }

        public static ApprovalResult expired() {
            return new ApprovalResult(Status.EXPIRED, null);
        }

        public static ApprovalResult success(Activation activation) {
            return new ApprovalResult(Status.SUCCESS, activation);
        }
    }

    public enum Status {
        SUCCESS,
        INVALID,
        EXPIRED,
    }
}
