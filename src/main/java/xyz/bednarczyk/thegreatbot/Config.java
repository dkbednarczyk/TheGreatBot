package xyz.bednarczyk.thegreatbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class Config {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheGreatBot-Config");
    private static final File configDir = FabricLoader.getInstance().getConfigDir().toFile();
    private static final File configFile = new File(configDir, "thegreatbot.json");

    public String botToken;
    public String verificationsChannelId;
    public String trackingChannelId;

    public Config() {
        this.botToken = "";
        this.verificationsChannelId = "";
        this.trackingChannelId = "";
    }

    public static Config load() {
        ObjectMapper mapper = new ObjectMapper();
        Config config;

        try {
            boolean justCreated = configFile.createNewFile();

            if (justCreated) {
                config = new Config();
                mapper.writeValue(configFile, config);

                LOGGER.info("The Great Bot config file created: {}", configFile.getAbsolutePath());
                LOGGER.warn("Please edit the config file and set the bot token and channel ID fields.");

                return null;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to create The Great Bot config file: {}", e.getMessage(), e);
            return null;
        }

        try {
            config = mapper.readValue(configFile, Config.class);
        } catch (Exception e) {
            LOGGER.error("Failed to load The Great Bot config file: {}", e.getMessage(), e);
            return null;
        }

        if (config.botToken == null || config.botToken.isEmpty()) {
            LOGGER.error("Bot token is not set in The Great Bot config file.");
            return null;
        }

        if (config.verificationsChannelId == null || config.verificationsChannelId.isEmpty()) {
            LOGGER.error("Verifications channel ID is not set in The Great Bot config file.");
            return null;
        }

        if (config.trackingChannelId == null || config.trackingChannelId.isEmpty()) {
            LOGGER.error("Tracking channel ID is not set in The Great Bot config file.");
            return null;
        }

        LOGGER.info("Configuration loaded successfully");
        return config;
    }
}
