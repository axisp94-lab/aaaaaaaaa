package net.amethystesmp.anpc.scripting;

import net.amethystesmp.anpc.npc.ManagedNPC;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.HashMap;
import java.util.Map;

public final class ScriptContext {
    private final ManagedNPC npc; private final Player player; private final Map<String,Object> locals=new HashMap<>();
    public ScriptContext(ManagedNPC npc,Player player){this.npc=npc;this.player=player;}
    public ManagedNPC npc(){return npc;} public Player player(){return player;} public Map<String,Object> locals(){return locals;}
    public Object value(String key){String k=key.trim(); if(k.equalsIgnoreCase("event entity")||k.equalsIgnoreCase("player"))return player; if(k.equalsIgnoreCase("name of npc"))return npc.id(); if(k.equalsIgnoreCase("location of npc"))return npc.location(); if(k.equalsIgnoreCase("x of npc"))return npc.location().getX(); if(k.equalsIgnoreCase("y of npc"))return npc.location().getY(); if(k.equalsIgnoreCase("z of npc"))return npc.location().getZ(); if(k.equalsIgnoreCase("name of player")&&player!=null)return player.getName(); if(k.equalsIgnoreCase("event item")&&player!=null)return player.getInventory().getItemInMainHand(); if(locals.containsKey(k))return locals.get(k); return k; }
}
