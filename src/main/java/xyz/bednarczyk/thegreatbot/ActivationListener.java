package xyz.bednarczyk.thegreatbot;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class ActivationListener extends ListenerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheGreatBot-Discord");

    private final Config config;
    private final ActivationService activationService;
    private final Supplier<MinecraftServer> serverSupplier;

    public ActivationListener(Config config, ActivationService activationService, Supplier<MinecraftServer> serverSupplier) {
        this.config = config;
        this.activationService = activationService;
        this.serverSupplier = serverSupplier;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (!event.isFromGuild()) return;
        if (!event.getGuildChannel().getId().equals(config.verificationsChannelId)) return;

        Message message = event.getMessage();
        String content = message.getContentRaw().trim();

        // Validate that the code is exactly 6 digits
        if (content.length() != 6 || !content.matches("\\d{6}")) {
            return;
        }

        MinecraftServer server = serverSupplier.get();
        if (server == null) {
            LOGGER.error("Cannot process activation code {} - server instance is null", content);
            event.getChannel().sendMessage("❌ Minecraft server is not ready right now. Please try again shortly.").queue();
            return;
        }

        String approverTag = event.getAuthor().getAsTag();
        String approverId = message.getAuthor().getId();
        var channel = event.getChannel();
        var guild = event.getGuild();

        server.execute(() -> {
            ActivationService.ApprovalResult result = activationService.approveActivation(content);
            if (result.status() == ActivationService.Status.INVALID) {
                channel.sendMessage("❌ Invalid or expired activation code.").queue();
                LOGGER.warn("Invalid activation code attempt: {} by user {}", content, approverTag);
                return;
            }

            if (result.status() == ActivationService.Status.EXPIRED) {
                channel.sendMessage("❌ This activation code has expired.").queue();
                return;
            }

            Activation activation = result.activation();
            ActivationState state = ActivationState.getServerState(server);
            state.activatePlayer(activation.getPlayerUUID(), activation.getPlayerName());

            channel.sendMessage("✅ Player **" + activation.getPlayerName() + "** has been activated!").queue();
            LOGGER.info("Player {} activated by Discord user {}", activation.getPlayerName(), approverTag);

            TextChannel tracking = guild.getChannelById(TextChannel.class, config.trackingChannelId);
            if (tracking != null) {
                tracking.sendMessage("✅ Player **" + activation.getPlayerName() + "** has been activated by <@" + approverId + ">.").queue();
            } else {
                LOGGER.error("Tracking channel ID not found: {}", config.trackingChannelId);
            }
        });
    }
}
