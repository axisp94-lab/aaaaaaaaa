package net.amethystesmp.anpc.npc;

import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.tracker.DummyTracker;
import kr.toxicity.model.api.profile.ModelProfile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Entity;

import java.util.HashSet;
import java.util.Set;

public final class ManagedNPC {
    private NPCDefinition definition;
    private final DummyTracker tracker;
    private final Set<String> viewers = new HashSet<>();
    private Location current;
    private final Interaction hitbox;
    private String animation = "idle";

    public ManagedNPC(NPCDefinition definition, ModelRenderer renderer, Location location) {
        this(definition, renderer, location, null);
    }
    public ManagedNPC(NPCDefinition definition, ModelRenderer renderer, Location location, ModelProfile.Uncompleted profile) {
        this.definition = definition;
        this.current = location.clone();
        this.tracker = profile == null ? renderer.create(BukkitAdapter.adapt(location)) : renderer.create(BukkitAdapter.adapt(location), profile);
        this.hitbox = location.getWorld().spawn(location, Interaction.class, entity -> {
            entity.setInteractionWidth(0.8f);
            entity.setInteractionHeight(1.8f);
            entity.setResponsive(true);
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.addScoreboardTag("anpc:" + definition.id().toLowerCase(java.util.Locale.ROOT));
        });
    }
    public String id() { return definition.id(); }
    public NPCDefinition definition() { return definition; }
    public Location location() { return current.clone(); }
    public DummyTracker tracker() { return tracker; }
    public void updateDefinition(NPCDefinition next) { definition = next; }
    public void show(Player player) {
        if (tracker.isClosed() || !player.getWorld().equals(current.getWorld())) return;
        if (viewers.add(player.getUniqueId().toString())) tracker.spawn(BukkitAdapter.adapt(player));
    }
    public void hide(Player player) {
        if (viewers.remove(player.getUniqueId().toString()) && !tracker.isClosed()) tracker.remove(BukkitAdapter.adapt(player));
    }
    public void move(Location target) {
        if (tracker.isClosed() || target.getWorld() == null) return;
        current = target.clone();
        tracker.location(BukkitAdapter.adapt(current));
        hitbox.teleport(current);
    }
    public boolean animate(String name) {
        if (tracker.isClosed()) return false;
        boolean ok = tracker.animate(name, AnimationModifier.DEFAULT);
        if (ok) animation = name;
        return ok;
    }
    public String animation() { return animation; }
    public Entity hitbox() { return hitbox; }
    public void close() { if (!tracker.isClosed()) tracker.close(); if (hitbox.isValid()) hitbox.remove(); viewers.clear(); }
}
