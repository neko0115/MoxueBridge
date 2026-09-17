package io.github.neko0115.moxuebridge;

import org.bukkit.plugin.java.JavaPlugin;

public final class MoxueBridgePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("MoxueBridge 0.1.0 enabled");
    }
}