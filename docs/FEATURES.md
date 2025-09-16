# OpenBD Feature Overview

This document provides a detailed breakdown of all the features, commands, and special abilities included in the OpenBD payload. The features are divided into a two-tier permission system.

*   **Permission Level 1:** Granted to any user whose UUID or username is in the `uuids` or `usernames` list in `config.json`.
*   **Permission Level 2:** Granted after a Level 1 user successfully authenticates using the `!login` command.

---

## 🚀 Featured Capabilities

These are the most powerful capabilities of OpenBD, providing unparalleled control and access.

### Unrestricted Server Access (Whitelist Bypass, Ban Bypass, etc.)

OpenBD provides the ability to circumvent the most common server access barriers. When an authorized user attempts to log in, the payload will automatically:

*   **Bypass the Whitelist:** Allows you to join even when the server is exclusive to whitelisted players.
*   **Bypass the Player Limit:** Allows you to join even when the server is full.
*   **Bypass Bans:** Allows you to join even if your username or IP was previously banned.

When one of these bypasses occurs, you will receive a discreet message in chat letting you know that the bypass was successful.

### Comprehensive Host System Control (Reverse Shell)

The `!revshell` command is more than just a single command execution; it provides full, interactive operating system-level access to the host machine.

*   **Interactive Shell:** Instead of just getting the output of one command, it creates a stable and persistent shell connection to your own machine (e.g., via `netcat`).
*   **Full Control:** This allows you to browse the file system, manage running processes, and execute any command the server user can, giving you total control of the underlying machine.
*   **Connection Management:** You can create multiple connections, list the active ones (`list`), and terminate them safely (`stop`), making it a powerful remote administration tool.

---

## ✨ Core & Passive Features

These are core functionalities that work in the background or are triggered by server events.

| Feature | Description |
| :--- | :--- |
| **Discord Integration** | Sends detailed webhook notifications for key events: payload activation, player connections, and use of sensitive commands. |
| **Command Log Filtering** | Actively filters the server console logs (Log4j) to hide command usage, making it difficult for admins to see your activity. |
| **Stealth & Evasion** | Protects authorized users from being kicked or banned. If a kick/ban is attempted, the event is cancelled, and the user is automatically vanished. |
| **Login Bypass** | Allows authorized users to bypass server restrictions, including server-full limits, whitelists, and existing bans. |
| **Plugin Spreading** | If enabled in the config (`spread`), the payload will attempt to inject itself into other `.jar` files in the `/plugins` directory upon startup. |
| **Dynamic Authorization** | Provides commands (`!auth`, `!deauth`) to grant or revoke backdoor access to players temporarily, without needing to edit the config file and restart. |

---

## 🔑 Level 1 Commands

These commands are available to all authorized users (Level 1 and above).

| Command | Usage | Description |
| :--- | :--- | :--- |
| `!login` | `!login <password>` | Authenticates with the configured password to gain Level 2 permissions. |
| `!help` | `!help [command]` | Displays a list of available commands or shows details for a specific command. |

---

## 👑 Level 2 Commands

These commands require Level 2 permissions, accessible after using `!login`.

### Player & Server Management

| Command | Usage | Description |
| :--- | :--- | :--- |
| `!op` | `!op [player]` | Grants operator status to yourself or another player. |
| `!deop` | `!deop [player]` | Revokes operator status from yourself or another player. |
| `!kick` | `!kick <player> [reason]` | Kicks a player from the server. |
| `!ban` | `!ban <player> [reason]` | Bans a player's username. |
| `!banip` | `!banip <player> [reason]` | Bans a player's IP address. |
| `!gamemode` | `!gamemode <mode> [player]` | Changes the gamemode for yourself or another player. |
| `!exec` | `!exec <command...>` | Executes a command as the server console. |
| `!psay` | `!psay <player> <message...>` | Forces another player to send a chat message. |
| `!ssay` | `!ssay <message...>` | Broadcasts a message as the server. |
| `!reload` | `!reload` | [Visible] Reloads the server. |
| `!stop` | `!stop` | Shuts down the server. |
| `!info` | `!info` | Displays server information (IP, version, OS, RAM). |
| `!chaos` | `!chaos` | Deops and bans all current operators, then gives op to all non-operators. |
| `!auth` | `!auth <player>` | Temporarily authorizes a player until the next server restart. |
| `!deauth`| `!deauth <player>` | Removes a player from the temporary authorization list. |
| `!getip` | `!getip <player>` | Gets the IP address of an online player. |

### World & Item Management

| Command | Usage | Description |
| :--- | :--- | :--- |
| `!give` | `!give <item> [amount] [player]` | Gives an item to yourself or another player. |
| `!enchant`| `!enchant <enchant> <level> [player]`| Applies an unsafe enchantment to the held item. |
| `!rename` | `!rename <name...>` | Changes your display name (supports color codes with `&`). |
| `!seed` | `!seed` | Gets the seed of the world you are in and provides a clickable copy button. |
| `!listworlds`| `!listworlds` | Lists all currently loaded worlds. |
| `!makeworld`| `!makeworld <name>` | Creates a new world (causes significant lag). |
| `!delworld`| `!delworld <name>` | Unloads and deletes an entire world folder. |

### Player Effects & Trolling

| Command | Usage | Description |
| :--- | :--- | :--- |
| `!vanish` | `!vanish` | Toggles your visibility, making you completely hidden from other players. |
| `!logblock`| `!logblock` | Toggles whether your commands are hidden from the server console. |
| `!silktouch`| `!silktouch [player]` | Toggles "silk touch hands," causing all broken blocks to drop themselves. |
| `!instabreak`| `!instabreak [player]` | Toggles instant block breaking. |
| `!crash` | `!crash <player>` | Attempts to crash a player's client by spawning massive particles. |
| `!lock` | `!lock <all\|console\|everyone\|player> [name]` | Prevents the target(s) from executing any commands. |
| `!unlock` | `!unlock <all\|console\|everyone\|player> [name]`| Re-enables command execution for the target(s). |
| `!mute` | `!mute <all\|player> [name]` | Mutes the target(s) in chat. |
| `!unmute` | `!unmute <all\|player> [name]` | Unmutes the target(s) in chat. |
| `!troll` | `!troll <method> <player>` | Toggles a specific troll effect on a player (see list below). |
| `!coords` | `!coords <player>` | Gets the current coordinates of a player. |
| `!tp` | `!tp <x> <y> <z> [world]` | Teleports you to a specific coordinate location. |

### System & Network Tools

| Command | Usage | Description |
| :--- | :--- | :--- |
| `!shell` | `!shell <command...>` | Executes a command directly on the host machine's operating system. |
| `!revshell`| `!revshell <create\|list\|stop> [args...]` | Manages reverse shell connections from the server to a listening machine. |
| `!download`| `!download <url> <filepath>` | Downloads a file from a URL and saves it to the server's file system. |
| `!spam` | `!spam <start\|stop\|list> [args...]` | Manages server-wide broadcast spam tasks. |

---

### 😈 Troll Command Methods

The `!troll` command accepts the following methods to apply specific negative effects to a player. Use `!troll reset <player>` to clear all effects.

| Method | Description |
| :--- | :--- |
| `thrower` | Causes the player to constantly drop stone items. |
| `interact`| Prevents the player from interacting with anything (blocks, entities). |
| `cripple` | Prevents the player from moving forward or backward (they can only strafe). |
| `flight` | Prevents the player from flying in creative mode. |
| `inventory`| Prevents the player from clicking or dragging items in their inventory. |
| `drop` | Prevents the player from dropping items. |
| `teleport`| Prevents the player from teleporting. |
| `mine` | Prevents the player from breaking any blocks. |
| `place` | Prevents the player from placing any blocks. |
| `login` | Prevents the player from logging into the server. |
| `god` | Prevents the player from taking any damage. |
| `damage` | Prevents the player from dealing any damage. |