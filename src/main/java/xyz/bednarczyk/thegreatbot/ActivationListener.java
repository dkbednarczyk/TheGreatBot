package xyz.bednarczyk.thegreatbot;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class ActivationListener extends ListenerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheGreatBot-Discord");

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (!event.isFromGuild()) return;
        if (!event.getGuildChannel().getId().equals(TheGreatBot.CONFIG.verificationsChannelId)) return;

        Message message = event.getMessage();
        String content = message.getContentRaw().trim();

        // Validate that the code is exactly 6 digits
        if (content.length() != 6 || !content.matches("\\d{6}")) {
            return;
        }

        Activation activation = Activation.get(content);
        if (activation == null) {
            event.getChannel().sendMessage("❌ Invalid or expired activation code.").queue();
            LOGGER.warn("Invalid activation code attempt: {} by user {}", content, event.getAuthor().getAsTag());
            return;
        }

        String playerName = activation.getPlayerName();
        UUID playerUUID = activation.getPlayerUUID();

        // Check if code is expired
        if (activation.isExpired()) {
            Activation.remove(content);
            event.getChannel().sendMessage("❌ This activation code has expired.").queue();
            LOGGER.info("Expired activation code used: {} for player {}", content, playerName);
            return;
        }

        // Remove from pending and add to activated
        Activation.remove(content);

        if (TheGreatBot.SERVER != null) {
            ActivationState state = ActivationState.getServerState(TheGreatBot.SERVER);
            state.activatePlayer(playerUUID, playerName);
        } else {
            LOGGER.error("Cannot activate player {} - server instance is null", playerName);
            return;
        }

        event.getChannel().sendMessage("✅ Player **" + playerName + "** has been activated!").queue();
        LOGGER.info("Player {} activated by Discord user {}", playerName, event.getAuthor().getAsTag());

        // Log to tracking channel
        TextChannel tracking = event.getGuild().getChannelById(TextChannel.class, TheGreatBot.CONFIG.trackingChannelId);
        if (tracking != null) {
            tracking.sendMessage("✅ Player **" + playerName + "** has been activated by <@" + message.getAuthor().getId() + ">.").queue();
        } else {
            LOGGER.error("Tracking channel ID not found: {}", TheGreatBot.CONFIG.trackingChannelId);
        }
    }
}
