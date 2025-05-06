# ClickCooldown

A plugin for Minecraft 1.12.2 that adds a cooldown (recharge) function for activators: buttons, pressure plates, and levers.

## Description

ClickCooldown allows you to set a cooldown time for any activator. When a player uses an activator with a configured cooldown, it functions normally and then temporarily disappears for the specified period. After the cooldown time expires, the activator reappears in the same place with the same orientation.

## Features

- Set cooldowns for buttons, pressure plates, and levers
- Preserve orientation and direction of activators when restored
- Support for all types of activators (stone, wooden, gold, iron)
- Activators maintain their state (on/off) for levers
- Proper functionality with redstone signals
- Recovery of activators after server restart
- Save all settings and active cooldowns

## Commands

- `/clickcooldown <seconds>` - sets the cooldown time for the activator the player is looking at

## Permissions

- `clickcooldown.use` - permission to use the command (default: operators only)

## How to Use

1. Aim at an activator (button, pressure plate, lever)
2. Enter the command `/clickcooldown <seconds>`, for example: `/clickcooldown 10`
3. Now when this activator is used, it will function and then disappear for 10 seconds
4. After the cooldown expires, the activator will automatically reappear

## Usage Examples

- **Door Button:** Set a 30-second cooldown on a button that opens doors. Players will only be able to open it once every 30 seconds.
- **Reward System:** Set a 24-hour cooldown (86400 seconds) on a lever that dispenses rewards. Players can receive rewards only once per day.
- **Pressure Plate Trap:** Set a 60-second cooldown on a pressure plate that activates a trap. This gives players time to escape before the trap becomes active again.

## Installation

1. Download the plugin
2. Place the JAR file in the `plugins` folder of your Minecraft server
3. Restart the server or use `/reload`
4. Done! The plugin is installed

## Requirements

- Minecraft 1.12.2
- Spigot or Paper server

## Technical Information

The plugin stores all information in the `plugins/ClickCooldown/cooldowns.yml` file:
- Cooldown settings for activators
- Active cooldowns and their expiration times
- Data about block type and orientation

When the server is shut down during an active cooldown, the information is saved, and upon the next startup, the activator will be restored at the right time with the correct orientation.

## Development

The project uses Maven for dependency management. Project structure:
- `ClickCooldown.java` - main plugin class
- `CooldownManager.java` - cooldown manager with save and restore logic

To build, use:
```bash
mvn clean package
```

## License

Free to use on any servers.
