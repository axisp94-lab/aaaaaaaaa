package net.amethystesmp.anpc;

import net.amethystesmp.anpc.command.ANPCCommand;
import net.amethystesmp.anpc.npc.NPCManager;
import net.amethystesmp.anpc.scripting.ScriptEngine;
import net.amethystesmp.anpc.storage.VariableStore;
import org.bukkit.plugin.java.JavaPlugin;

public final class ANPCPlugin extends JavaPlugin {
    private NPCManager npcManager;
    private ScriptEngine scriptEngine;
    private VariableStore variableStore;

    @Override public void onEnable() {
        saveDefaultConfig();
        saveResource("npc.yml", false);
        getDataFolder().toPath().resolve("scripts").toFile().mkdirs();
        getDataFolder().toPath().resolve("npcs").toFile().mkdirs();
        if (getServer().getPluginManager().getPlugin("BetterModel") == null) {
            getLogger().severe("BetterModel 2.2.0 est requis pour afficher les NPC. Plugin désactivé.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            variableStore = new VariableStore(this);
            npcManager = new NPCManager(this);
            scriptEngine = new ScriptEngine(this, npcManager, variableStore);
            npcManager.load();
            scriptEngine.loadAll();
            ANPCCommand command = new ANPCCommand(this);
            getCommand("anpc").setExecutor(command);
            getCommand("anpc").setTabCompleter(command);
            getServer().getPluginManager().registerEvents(scriptEngine, this);
            getLogger().info("AmethyysNPCs active : " + npcManager.size() + " NPC charge(s).");
        } catch (Exception e) {
            getLogger().severe("Impossible d'initialiser AmethyysNPCs : " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override public void onDisable() {
        if (scriptEngine != null) scriptEngine.shutdown();
        if (npcManager != null) npcManager.shutdown();
        if (variableStore != null) variableStore.close();
    }
    public NPCManager npcs() { return npcManager; }
    public ScriptEngine scripts() { return scriptEngine; }
    public VariableStore variables() { return variableStore; }
}
