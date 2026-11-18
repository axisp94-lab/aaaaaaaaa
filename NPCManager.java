package net.amethystesmp.anpc.npc;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.profile.ModelProfile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import net.amethystesmp.anpc.ANPCPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class NPCManager {
    private final ANPCPlugin plugin;
    private final Map<String, ManagedNPC> npcs = new ConcurrentHashMap<>();
    private BukkitTask visibilityTask;
    private File file;
    private YamlConfiguration yaml;
    public NPCManager(ANPCPlugin plugin) { this.plugin = plugin; }
    public void load() {
        file = new File(plugin.getDataFolder(), "npc.yml"); yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("npcs");
        if (root != null) for (String id : root.getKeys(false)) {
            try { createFrom(NPCDefinition.read(id, root.getConfigurationSection(id))); }
            catch (Exception e) { plugin.getLogger().warning("NPC '" + id + "' non charge : " + e.getMessage()); }
        }
        visibilityTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshVisibility, 10L, 10L);
    }
    private void createFrom(NPCDefinition def) {
        List<World> targets = new ArrayList<>();
        if (def.worlds().contains("all")) targets.addAll(Bukkit.getWorlds());
        else for (String worldName : def.worlds()) {
            World w = Bukkit.getWorld(worldName);
            if (w == null) plugin.getLogger().severe("Monde '" + worldName + "' absent/non charge pour le NPC " + def.id());
            else targets.add(w);
        }
        for (World world : targets) {
            ModelRenderer renderer = resolveRenderer(def);
            if (renderer == null) { plugin.getLogger().severe("Modele BetterModel introuvable pour " + def.id() + " : " + def.type()); continue; }
            ManagedNPC managed = new ManagedNPC(def, renderer, def.location(world), skinProfile(def));
            String key = def.worlds().contains("all") ? def.id().toLowerCase(Locale.ROOT) + "@" + world.getName().toLowerCase(Locale.ROOT) : def.id().toLowerCase(Locale.ROOT);
            ManagedNPC previous = npcs.put(key, managed);
            if (previous != null) previous.close();
        }
    }
    private ModelProfile.Uncompleted skinProfile(NPCDefinition def) {
        if (!def.type().equalsIgnoreCase("player") && !def.type().toLowerCase(Locale.ROOT).startsWith("limb:")) return null;
        if (def.skin().isBlank()) return null;
        try { return ModelProfile.of(java.util.UUID.fromString(def.skin())); }
        catch (IllegalArgumentException ignored) {
            Player online = Bukkit.getPlayerExact(def.skin());
            return online == null ? null : ModelProfile.of(BukkitAdapter.adapt(online)).asUncompleted();
        }
    }
    private ModelRenderer resolveRenderer(NPCDefinition def) {
        String type = def.type();
        if (type.equalsIgnoreCase("player")) return BetterModel.limbOrNull("steve");
        if (type.toLowerCase(Locale.ROOT).startsWith("limb:")) return BetterModel.limbOrNull(type.substring(5));
        return BetterModel.modelOrNull(type);
    }
    private ModelRenderer resolveRenderer(String type) {
        if (type.equalsIgnoreCase("player")) return BetterModel.limbOrNull("steve");
        if (type.toLowerCase(Locale.ROOT).startsWith("limb:")) return BetterModel.limbOrNull(type.substring(5));
        return BetterModel.modelOrNull(type);
    }
    private void refreshVisibility() {
        double range = plugin.getConfig().getDouble("tracking-range", 64);
        double r2 = range * range;
        for (ManagedNPC npc : npcs.values()) for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(npc.location().getWorld()) && p.getLocation().distanceSquared(npc.location()) <= r2) npc.show(p);
            else npc.hide(p);
        }
    }
    public ManagedNPC get(String id) { String key=id.toLowerCase(Locale.ROOT); ManagedNPC exact=npcs.get(key); if(exact!=null)return exact; return npcs.values().stream().filter(n->n.id().equalsIgnoreCase(id)).findFirst().orElse(null); }
    public Collection<ManagedNPC> all() { return Collections.unmodifiableCollection(npcs.values()); }
    public int size() { return npcs.size(); }
    public boolean create(String id, String type, Location loc) {
        if (get(id) != null || loc.getWorld() == null) return false;
        NPCDefinition def = new NPCDefinition(id, id, type, List.of(loc.getWorld().getName()), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(), "");
        ModelRenderer renderer = resolveRenderer(type); if (renderer == null) return false;
        ManagedNPC managed = new ManagedNPC(def, renderer, loc, skinProfile(def)); npcs.put(id.toLowerCase(Locale.ROOT), managed); saveDefinition(def); refreshVisibility(); return true;
    }
    public void remove(String id) { String key=id.toLowerCase(Locale.ROOT); List<String> matches=npcs.entrySet().stream().filter(e->e.getValue().id().equalsIgnoreCase(id)).map(Map.Entry::getKey).toList(); for(String k:matches){ManagedNPC npc=npcs.remove(k);if(npc!=null)npc.close();} yaml.set("npcs."+id, null); save(); }
    public void rename(String id, String display) { ManagedNPC n=get(id); if(n==null)return; NPCDefinition d=n.definition(); NPCDefinition next=new NPCDefinition(d.id(),display,d.type(),d.worlds(),d.x(),d.y(),d.z(),d.yaw(),d.pitch(),d.skin()); n.updateDefinition(next); yaml.set("npcs."+id+".display-name",display); save(); }
    public void center(String id) { ManagedNPC n=get(id); if(n==null)return; Location l=n.location(); l.setX(l.getBlockX()+.5); l.setZ(l.getBlockZ()+.5); n.move(l); persistPosition(n,l); }
    public void teleport(String id, Location loc) { ManagedNPC n=get(id); if(n==null||loc.getWorld()==null)return; n.move(loc); persistPosition(n,loc); refreshVisibility(); }
    private void persistPosition(ManagedNPC n, Location l) { NPCDefinition d=n.definition(); NPCDefinition next=new NPCDefinition(d.id(),d.displayName(),d.type(),List.of(l.getWorld().getName()),l.getX(),l.getY(),l.getZ(),l.getYaw(),l.getPitch(),d.skin()); n.updateDefinition(next); saveDefinition(next); }
    private void saveDefinition(NPCDefinition d) { String p="npcs."+d.id()+"."; yaml.set(p+"display-name",d.displayName()); yaml.set(p+"type",d.type()); yaml.set(p+"world",d.worlds()); yaml.set(p+"x",d.x()); yaml.set(p+"y",d.y()); yaml.set(p+"z",d.z()); yaml.set(p+"yaw",d.yaw()); yaml.set(p+"pitch",d.pitch()); if(!d.skin().isBlank())yaml.set(p+"skin",d.skin()); save(); }
    private void save() { try { yaml.save(file); } catch(IOException e) { plugin.getLogger().severe("Ecriture npc.yml impossible : "+e.getMessage()); } }
    public void reload() { shutdownNPCs(); npcs.clear(); load(); }
    private void shutdownNPCs() { if(visibilityTask!=null)visibilityTask.cancel(); npcs.values().forEach(ManagedNPC::close); }
    public void shutdown() { shutdownNPCs(); npcs.clear(); }
}
