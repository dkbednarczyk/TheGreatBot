package xyz.bednarczyk.thegreatbot;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class Activation {
    public static final long CODE_EXPIRY_MINUTES = 30;

    private final String code;
    private final UUID playerUUID;
    private final String playerName;
    private final long timestamp;

    public Activation(String code, ServerPlayer player) {
        this.code = code;
        this.playerUUID = player.getUUID();
        this.playerName = player.getName().getString();
        this.timestamp = System.currentTimeMillis();
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - timestamp > TimeUnit.MINUTES.toMillis(CODE_EXPIRY_MINUTES);
    }

    public String getCode() {
        return code;
    }

    public UUID getPlayerUUID() {
        return this.playerUUID;
    }

    public String getPlayerName() {
        return this.playerName;
    }
}
