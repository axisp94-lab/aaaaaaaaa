package net.amethystesmp.anpc.command;

import net.amethystesmp.anpc.ANPCPlugin;
import net.amethystesmp.anpc.npc.ManagedNPC;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

public final class ANPCCommand implements TabExecutor {
    private final ANPCPlugin plugin;
    public ANPCCommand(ANPCPlugin plugin){this.plugin=plugin;}
    private void msg(CommandSender s,String text){s.sendMessage(ChatColor.translateAlternateColorCodes('&',"&6[ANPC] &f"+text));}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!sender.hasPermission("anpc.admin")){msg(sender,"Permission insuffisante.");return true;}
        if(args.length==0||args[0].equalsIgnoreCase("help")){msg(sender,"/anpc list | create <id> <modele> | tp <id> | center <id> | rename <id> <nom> | kill <id> | reload");return true;}
        switch(args[0].toLowerCase(Locale.ROOT)){
            case "list"->{if(plugin.npcs().all().isEmpty())msg(sender,"Aucun NPC chargé.");else plugin.npcs().all().forEach(n->msg(sender,n.id()+" — "+n.definition().type()+" — "+n.location().getWorld().getName()));}
            case "create"->{if(!(sender instanceof Player p)){msg(sender,"Commande réservée aux joueurs.");return true;}if(args.length<3){msg(sender,"Usage : /anpc create <id> <modele|limb:skin>");return true;}if(plugin.npcs().create(args[1],args[2],p.getLocation()))msg(sender,"NPC créé : "+args[1]);else msg(sender,"Création impossible : ID déjà utilisé ou modèle BetterModel introuvable.");}
            case "tp"->{if(!(sender instanceof Player p)||args.length<2){msg(sender,"Usage : /anpc tp <id> (joueur)");return true;}ManagedNPC n=plugin.npcs().get(args[1]);if(n==null){msg(sender,"NPC introuvable.");return true;}p.teleport(n.location());}
            case "center"->{if(args.length<2){msg(sender,"Usage : /anpc center <id>");return true;}plugin.npcs().center(args[1]);msg(sender,"NPC centré (si trouvé).");}
            case "rename"->{if(args.length<3){msg(sender,"Usage : /anpc rename <id> <nouveau nom>");return true;}plugin.npcs().rename(args[1],String.join(" ",Arrays.copyOfRange(args,2,args.length)));msg(sender,"Nom modifié (si NPC trouvé).");}
            case "kill","remove","delete"->{if(args.length<2){msg(sender,"Usage : /anpc kill <id>");return true;}plugin.npcs().remove(args[1]);msg(sender,"NPC supprimé (si trouvé).");}
            case "reload"->{plugin.scripts().shutdown();plugin.npcs().reload();plugin.scripts().loadAll();msg(sender,"Configuration, NPC et scripts rechargés.");}
            default->msg(sender,"Sous-commande inconnue. /anpc help");
        }return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){if(args.length==1)return filter(List.of("help","list","create","tp","center","rename","kill","reload"),args[0]);if(args.length==2&&Set.of("tp","center","rename","kill","remove","delete").contains(args[0].toLowerCase(Locale.ROOT)))return filter(plugin.npcs().all().stream().map(ManagedNPC::id).toList(),args[1]);return Collections.emptyList();}
    private List<String> filter(List<String> values,String prefix){return values.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))).toList();}
}
