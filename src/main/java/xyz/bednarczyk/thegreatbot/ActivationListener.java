package xyz.bednarczyk.thegreatbot;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        Activation activation = Activation.pendingActivations.get(content);
        if (activation == null) {
            event.getChannel().sendMessage("❌ Invalid or expired activation code.").queue();
            LOGGER.warn("Invalid activation code attempt: {} by user {}", content, event.getAuthor().getAsTag());
            return;
        }

        // Check if code is expired
        if (activation.isExpired()) {
            Activation.pendingActivations.remove(content);
            event.getChannel().sendMessage("❌ This activation code has expired.").queue();
            LOGGER.info("Expired activation code used: {} for player {}", content, activation.playerName);
            return;
        }

        String playerName = activation.playerName;

        // Remove from pending and add to activated
        Activation.pendingActivations.remove(content);
        ActivationState.appendPlayer(playerName);

        event.getChannel().sendMessage("✅ Player **" + playerName + "** has been activated!").queue();
        LOGGER.info("Player {} activated by Discord user {}", playerName, event.getAuthor().getAsTag());

        // Log to tracking channel
        TextChannel tracking = event.getGuild().getChannelById(TextChannel.class, TheGreatBot.CONFIG.trackingChannelId);
        if (tracking != null) {
            tracking.sendMessage("✅ Player **" + playerName + "** has been activated by <@" + message.getAuthor().getId() + ">.").queue();
        } else {
            LOGGER.error("Tracking channel ID not found: {}", TheGreatBot.CONFIG.trackingChannelId);
        }

        // Notify the player in-game if they're online
        if (TheGreatBot.SERVER != null) {
            ServerPlayerEntity player = TheGreatBot.SERVER.getPlayerManager().getPlayer(playerName);
            if (player != null) {
                player.sendMessage(
                        Text.literal("✓ Your account has been activated! Welcome to the server!")
                            .styled(style -> style.withColor(Formatting.GREEN))
                );

                if (!player.isCreative() && !player.isSpectator()) {
                    player.changeGameMode(GameMode.SURVIVAL);
                }

                LOGGER.info("Notified online player {} of activation", playerName);
            }
        }
    }
}
