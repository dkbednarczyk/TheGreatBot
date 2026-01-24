# TheGreatBot

A Minecraft Fabric server-side mod that integrates Discord bot functionality to verify players before allowing them to join the server. Players must verify themselves by entering a unique activation code in a designated Discord channel.

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.18.4+
- Fabric API 0.141.1+
- A Discord bot token
- A Discord server with appropriate channels

## Installation

1. Build the mod and place it in your server's `mods` folder
2. Start the server once to generate the config file
3. Stop the server and configure the bot (see Configuration section)
4. Restart the server

## Configuration

On first run, the mod creates a config file at `config/thegreatbot.json`:

```json
{
  "botToken": "",
  "verificationsChannelId": "",
  "trackingChannelId": ""
}
```

### Configuration Fields

- **botToken**: Your Discord bot token
- **verificationsChannelId**: The Discord channel ID where players submit activation codes
- **trackingChannelId**: Channel ID for logging player join events

## Building from Source

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/`. Use the one without a tag like `-src`.
