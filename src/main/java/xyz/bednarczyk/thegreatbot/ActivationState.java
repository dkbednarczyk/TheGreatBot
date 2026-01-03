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

public class ActivationState extends PersistentState {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheGreatBot-State");
    public final Set<String> activatedPlayersSet;
    public String activatedPlayers;

    private ActivationState(){
        activatedPlayers = "";
        activatedPlayersSet = new HashSet<>();
    }

    private ActivationState(String activatedPlayers) {
        this.activatedPlayers = activatedPlayers;
        this.activatedPlayersSet = new HashSet<>();

        for (String p : activatedPlayers.split(",")) {
            if (!p.isEmpty()) {
                activatedPlayersSet.add(p);
            }
        }
    }

    private String getActivatedPlayers() {
        return activatedPlayers;
    }

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

    public static ActivationState getServerState(MinecraftServer server) {
        ServerWorld world = server.getWorld(World.OVERWORLD);
        assert world != null;

        ActivationState state = world.getPersistentStateManager().getOrCreate(type);

        state.markDirty();

        return state;
    }

    public static void appendPlayer(String playerName) {
        MinecraftServer server = TheGreatBot.SERVER;
        assert server != null;

        ActivationState state = getServerState(server);

        if (state.activatedPlayersSet.contains(playerName)) {
            LOGGER.info("Player {} is already activated.", playerName);
            return;
        }

        if (!state.activatedPlayers.isEmpty()) {
            state.activatedPlayers += ",";
        }
        state.activatedPlayers += playerName;
        state.activatedPlayersSet.add(playerName);

        state.markDirty();
        LOGGER.info("Player {} has been added to activated players.", playerName);
    }
}
