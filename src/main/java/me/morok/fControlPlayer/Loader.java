package me.morok.fControlPlayer;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import me.morok.fControlPlayer.command.ControlCommand;
import me.morok.fControlPlayer.config.Messages;
import me.morok.fControlPlayer.control.ControlService;
import me.morok.fControlPlayer.listener.ControlListener;
import me.morok.fControlPlayer.protocol.ControlPacketBridge;
import me.morok.fControlPlayer.protocol.compat.ProtocolLibCompat;
import me.morok.fControlPlayer.protocol.compat.ProtocolLibCompatFactory;
import me.morok.fControlPlayer.protocol.compat.ProtocolLibVersion;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class Loader extends JavaPlugin {
    private ControlService controlService;
    private ControlPacketBridge packetBridge;

    @Override
    public void onEnable() {
        ProtocolLibVersion protocolLibVersion = resolveProtocolLibVersion();
        if (protocolLibVersion == null) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        Messages messages = new Messages(this);
        ProtocolLibCompat compat = ProtocolLibCompatFactory.create(protocolLibVersion);
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        controlService = new ControlService(this, protocolManager, messages);
        packetBridge = new ControlPacketBridge(this, controlService, protocolManager, compat);
        packetBridge.register();

        ControlCommand controlCommand = new ControlCommand(controlService, messages);
        PluginCommand command = getCommand("control");
        if (command == null) {
            getLogger().warning("Command /control not registered in plugin.yml");
        } else {
            command.setExecutor(controlCommand);
            command.setTabCompleter(controlCommand);
        }

        getServer().getPluginManager().registerEvents(new ControlListener(controlService), this);
    }

    @Override
    public void onDisable() {
        if (packetBridge != null) {
            packetBridge.unregister();
        }
        if (controlService != null) {
            controlService.shutdown();
        }
    }

    private ProtocolLibVersion resolveProtocolLibVersion() {
        Plugin protocolLib = getServer().getPluginManager().getPlugin("ProtocolLib");
        if (protocolLib == null) {
            getLogger().severe("ProtocolLib is missing. Plugin cannot start.");
            return null;
        }
        String rawVersion = protocolLib.getDescription().getVersion();
        ProtocolLibVersion version = ProtocolLibVersion.parse(rawVersion);
        if (version == null) {
            getLogger().severe("Unsupported ProtocolLib version: " + rawVersion
                + ". Supported: 4.6.0, 4.7.0, 4.8.0, 5.0.0, 5.1.0, 5.2.0, 5.3.0, 5.4.0.");
            return null;
        }
        getLogger().info("Detected ProtocolLib " + rawVersion + " (compat " + version + ").");
        return version;
    }
}
