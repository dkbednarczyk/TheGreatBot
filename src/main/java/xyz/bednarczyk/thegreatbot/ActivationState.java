package xyz.bednarczyk.thegreatbot;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ActivationState extends SavedData {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheGreatBot-State");
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    private static final Codec<ActivationState> CODEC = Codec.either(UUID_CODEC.listOf(), Codec.STRING)
            .xmap(
                    value -> value.map(ActivationState::new, ActivationState::fromLegacyString),
                    state -> com.mojang.datafixers.util.Either.left(state.getActivatedPlayers())
            );
    private static final SavedDataType<ActivationState> type = new SavedDataType<>(
            Identifier.withDefaultNamespace(TheGreatBot.MOD_ID),
            ActivationState::new,
            CODEC,
            DataFixTypes.LEVEL
    );
    private final Set<UUID> activatedPlayersSet;

    private ActivationState() {
        activatedPlayersSet = new HashSet<>();
    }

    private ActivationState(List<UUID> activatedPlayers) {
        this.activatedPlayersSet = new HashSet<>(activatedPlayers);
    }

    private static ActivationState fromLegacyString(String activatedPlayers) {
        List<UUID> parsedPlayers = new ArrayList<>();
        for (String entry : activatedPlayers.split(",")) {
            if (!entry.isEmpty()) {
                parsedPlayers.add(UUID.fromString(entry));
            }
        }
        return new ActivationState(parsedPlayers);
    }

    public static ActivationState getServerState(MinecraftServer server) {
        ServerLevel world = server.getLevel(Level.OVERWORLD);
        assert world != null;

        return world.getDataStorage().computeIfAbsent(type);
    }

    private List<UUID> getActivatedPlayers() {
        return new ArrayList<>(activatedPlayersSet);
    }

    public boolean isActivated(UUID playerUUID) {
        return activatedPlayersSet.contains(playerUUID);
    }

    public void activatePlayer(UUID playerUUID, String playerName) {
        if (activatedPlayersSet.contains(playerUUID)) {
            LOGGER.info("Player {} is already activated.", playerName);
            return;
        }

        activatedPlayersSet.add(playerUUID);

        setDirty();
        LOGGER.info("Player {} (UUID: {}) has been added to activated players.", playerName, playerUUID);
    }
}
