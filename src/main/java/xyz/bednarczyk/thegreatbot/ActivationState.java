package xyz.bednarczyk.thegreatbot;

import com.mojang.serialization.Codec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ActivationState extends PersistentState {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheGreatBot-State");
    private static final Codec<ActivationState> CODEC = Codec.STRING.fieldOf("activatedPlayers").codec().xmap(
            ActivationState::new,
            ActivationState::getActivatedPlayers
    );
    private static final PersistentStateType<ActivationState> type = new PersistentStateType<>(
            TheGreatBot.MOD_ID,
            ActivationState::new,
            CODEC,
            null
    );
    private final Set<UUID> activatedPlayersSet;
    private String activatedPlayers;

    private ActivationState() {
        activatedPlayers = "";
        activatedPlayersSet = new HashSet<>();
    }

    private ActivationState(String activatedPlayers) {
        this.activatedPlayers = activatedPlayers;
        this.activatedPlayersSet = new HashSet<>();

        for (String p : activatedPlayers.split(",")) {
            if (!p.isEmpty()) {
                activatedPlayersSet.add(UUID.fromString(p));
            }
        }
    }

    public static ActivationState getServerState(MinecraftServer server) {
        ServerWorld world = server.getWorld(World.OVERWORLD);
        assert world != null;

        return world.getPersistentStateManager().getOrCreate(type);
    }

    private String getActivatedPlayers() {
        return activatedPlayers;
    }

    public boolean isActivated(UUID playerUUID) {
        return activatedPlayersSet.contains(playerUUID);
    }

    public void activatePlayer(UUID playerUUID, String playerName) {
        if (activatedPlayersSet.contains(playerUUID)) {
            LOGGER.info("Player {} is already activated.", playerName);
            return;
        }

        if (!activatedPlayers.isEmpty()) {
            activatedPlayers += ",";
        }

        activatedPlayers += playerUUID.toString();
        activatedPlayersSet.add(playerUUID);

        markDirty();
        LOGGER.info("Player {} (UUID: {}) has been added to activated players.", playerName, playerUUID);
    }
}
