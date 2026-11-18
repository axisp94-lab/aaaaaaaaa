package net.amethystesmp.anpc.npc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record NPCDefinition(String id, String displayName, String type, List<String> worlds,
                            double x, double y, double z, float yaw, float pitch, String skin) {
    public static NPCDefinition read(String id, ConfigurationSection s) {
        List<String> worlds = new ArrayList<>();
        Object raw = s.get("world");
        if (raw instanceof String str) worlds.add(str);
        else if (raw instanceof List<?> list) { for (Object o : list) if (o instanceof String v) worlds.add(v); }
        else if (raw == null) worlds.add("world");
        return new NPCDefinition(id, s.getString("display-name", id), s.getString("type", "villager"),
                worlds, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float)s.getDouble("yaw"), (float)s.getDouble("pitch"), s.getString("skin", ""));
    }
    public boolean isModel() { return type.toLowerCase(Locale.ROOT).startsWith("limb:") || !type.isBlank(); }
    public Location location(World world) { return new Location(world, x, y, z, yaw, pitch); }
    public boolean appliesTo(World world) { return worlds.contains("all") || worlds.contains(world.getName()); }
}
