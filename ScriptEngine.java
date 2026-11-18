package net.amethystesmp.anpc.scripting;

import net.amethystesmp.anpc.ANPCPlugin;
import net.amethystesmp.anpc.npc.ManagedNPC;
import net.amethystesmp.anpc.npc.NPCManager;
import net.amethystesmp.anpc.storage.VariableStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ScriptEngine implements Listener {
    private record Instruction(int indent,String text,int line){}
    private final ANPCPlugin plugin; private final NPCManager npcs; private final VariableStore variables;
    private final Map<String,Map<String,List<Instruction>>> scripts=new ConcurrentHashMap<>();
    private final Set<BukkitTask> tasks=ConcurrentHashMap.newKeySet();
    private final Map<String,Set<UUID>> nearbyPlayers=new ConcurrentHashMap<>();
    public ScriptEngine(ANPCPlugin plugin,NPCManager npcs,VariableStore variables){this.plugin=plugin;this.npcs=npcs;this.variables=variables;}
    public void loadAll(){scripts.clear();File dir=new File(plugin.getDataFolder(),"scripts");File[] fs=dir.listFiles((d,n)->n.endsWith(".aa"));if(fs==null)return;for(File f:fs){String id=f.getName().substring(0,f.getName().length()-3).toLowerCase(Locale.ROOT);try{scripts.put(id,parse(Files.readAllLines(f.toPath())));}catch(Exception e){plugin.getLogger().warning("Erreur script "+f.getName()+" : "+e.getMessage());}} scheduleIntervals(); scheduleProximity(); for(ManagedNPC npc:npcs.all())fire(npc.id(),"spawn",null);}
    private Map<String,List<Instruction>> parse(List<String> lines){Map<String,List<Instruction>> result=new LinkedHashMap<>();String event=null;for(int i=0;i<lines.size();i++){String raw=lines.get(i);if(raw.isBlank()||raw.stripLeading().startsWith("#"))continue;int indent=raw.length()-raw.stripLeading().length();String t=raw.trim();if(indent==0&&t.toLowerCase(Locale.ROOT).startsWith("on ")&&t.endsWith(":")){event=t.substring(3,t.length()-1).trim().toLowerCase(Locale.ROOT);result.put(event,new ArrayList<>());}else if(event!=null)result.get(event).add(new Instruction(indent,t,i+1));}return result;}
    private void scheduleIntervals(){for(var entry:scripts.entrySet()){List<Instruction> code=entry.getValue().get("interval");if(code==null)continue;long period=plugin.getConfig().getLong("script-default-interval",20);BukkitTask task=Bukkit.getScheduler().runTaskTimer(plugin,()->{ManagedNPC npc=npcs.get(entry.getKey());if(npc!=null)execute(npc,null,code,0);},period,period);tasks.add(task);}}
    private void scheduleProximity(){BukkitTask task=Bukkit.getScheduler().runTaskTimer(plugin,()->{double radius=plugin.getConfig().getDouble("script-approach-radius",3.0);double r2=radius*radius;for(ManagedNPC npc:npcs.all()){String id=npc.id().toLowerCase(Locale.ROOT);Set<UUID> current=new HashSet<>();for(Player p:npc.location().getWorld().getPlayers())if(p.getLocation().distanceSquared(npc.location())<=r2){current.add(p.getUniqueId());if(nearbyPlayers.computeIfAbsent(id,k->ConcurrentHashMap.newKeySet()).add(p.getUniqueId()))fire(id,"entity approach",p);}Set<UUID> previous=nearbyPlayers.computeIfAbsent(id,k->ConcurrentHashMap.newKeySet());for(UUID uuid:new HashSet<>(previous))if(!current.contains(uuid)){Player p=Bukkit.getPlayer(uuid);previous.remove(uuid);if(p!=null)fire(id,"entity leave",p);}}},10L,10L);tasks.add(task);}
    public void fire(String npc,String event,Player player){Map<String,List<Instruction>> map=scripts.get(npc.toLowerCase(Locale.ROOT));if(map==null)return;List<Instruction> code=map.get(event.toLowerCase(Locale.ROOT));ManagedNPC managed=npcs.get(npc);if(code!=null&&managed!=null)execute(managed,player,code,0);}
    private void execute(ManagedNPC npc,Player player,List<Instruction> code,int baseIndent){ScriptContext ctx=new ScriptContext(npc,player);runBlock(ctx,code,0,baseIndent);}
    private int runBlock(ScriptContext c,List<Instruction> code,int index,int indent){while(index<code.size()){Instruction in=code.get(index);if(in.indent()<indent)break;if(in.indent()>indent){index++;continue;}String s=in.text();int line=in.line();
        if(s.startsWith("if ")&&s.endsWith(":")){int end=findEnd(code,index+1,in.indent());if(test(c,s.substring(3,s.length()-1))){int child=index+1<code.size()?code.get(index+1).indent():in.indent()+4;runBlock(c,code,index+1,child);}index=end;continue;}
        if(s.startsWith("set ")){String body=s.substring(4).trim();int q=body.indexOf('"',1);if(body.startsWith("\"")&&q>0){String name=body.substring(1,q);String val=body.substring(q+1).trim();if(val.startsWith("(" )&&val.endsWith(")"))val=val.substring(1,val.length()-1);Object value=resolve(c,val);c.locals().put(name,value);storeVariable(c,name,value);}index++;continue;}
        if(s.startsWith("create variable ")){createVariable(c,s.substring(15).trim());index++;continue;}
        if(s.startsWith("say local ")||s.startsWith("say global ")){boolean global=s.startsWith("say global ");String msg=unquote(s.substring(global?11:10));String rendered=ChatColor.translateAlternateColorCodes('&',c.npc().definition().displayName()+" » "+msg);if(global)Bukkit.broadcastMessage(rendered);else if(c.player()!=null)c.player().sendMessage(rendered);else for(Player p:c.npc().location().getWorld().getPlayers())if(p.getLocation().distanceSquared(c.npc().location())<32*32)p.sendMessage(rendered);index++;continue;}
        if(s.startsWith("send to ")){int pos=s.indexOf(' ',8);if(pos>0){String target=s.substring(8,pos);String msg=unquote(s.substring(pos+1));Player p=target.equals("@p")?nearest(c.npc().location()):Bukkit.getPlayerExact(target);if(p!=null)p.sendMessage(ChatColor.translateAlternateColorCodes('&',msg));}index++;continue;}
        if(s.startsWith("play anime ")){c.npc().animate(unquote(s.substring(11)));index++;continue;}
        if(s.startsWith("play sound ")){String name=unquote(s.substring(11));try{Sound sound=Sound.valueOf(name.toUpperCase(Locale.ROOT).replace('.','_'));c.npc().location().getWorld().playSound(c.npc().location(),sound,1f,1f);}catch(IllegalArgumentException ignored){}index++;continue;}
        if(s.startsWith("move to ")){Location target=parseLocation(c,s.substring(8).trim());if(target!=null)move(c.npc(),target);index++;continue;}
        if(s.startsWith("tp ")){Location target=parseLocation(c,s.substring(3).trim());if(target!=null)move(c.npc(),target);index++;continue;}
        if(s.startsWith("flag on ")||s.startsWith("flag off ")){boolean on=s.startsWith("flag on ");String name=s.substring(on?8:9).replace("(","").replace(")","").trim();variables.set("npc",c.npc().id(),"flag:"+name,on);index++;continue;}
        if(s.startsWith("wait ")){long ticks=parseDurationTicks(s.substring(5));List<Instruction> remaining=new ArrayList<>(code.subList(index+1,code.size()));BukkitTask task=Bukkit.getScheduler().runTaskLater(plugin,()->runBlock(c,remaining,0,indent),ticks);tasks.add(task);return code.size();}
        if(s.startsWith("run command ")){String cmd=unquote(s.substring(12));Bukkit.dispatchCommand(Bukkit.getConsoleSender(),cmd.replace("%player%",c.player()==null?"":c.player().getName()));index++;continue;}
        if(s.startsWith("look at ")&&c.player()!=null){Location at=c.player().getLocation();Location from=c.npc().location();from.setDirection(at.toVector().subtract(from.toVector()));c.npc().move(from);index++;continue;}
        if(s.equals("stop follow")||s.equals("stop attack")){index++;continue;}
        if(s.startsWith("set state ")){c.locals().put("state",unquote(s.substring(10)));index++;continue;}
        index++;
    }return index;}
    private int findEnd(List<Instruction> c,int from,int parent){int i=from;while(i<c.size()&&c.get(i).indent()>parent)i++;return i;}
    private boolean test(ScriptContext c,String expression){String e=expression.trim();if(e.startsWith("flag (")&&e.contains(") is ")){String name=e.substring(6,e.indexOf(')'));boolean v=Boolean.parseBoolean(variables.get("npc",c.npc().id(),"flag:"+name,"false"));return e.endsWith("on")?v:!v;}String[] ops={" >= "," <= "," != "," == "," > "," < "," contains "};for(String op:ops){int p=e.indexOf(op);if(p>0){Object a=resolve(c,e.substring(0,p)),b=resolve(c,e.substring(p+op.length()));if(op.equals(" contains "))return String.valueOf(a).contains(String.valueOf(b));double x=num(a),y=num(b);return switch(op.trim()){case ">"->x>y;case "<"->x<y;case ">="->x>=y;case "<="->x<=y;case "!="->!Objects.equals(String.valueOf(a),String.valueOf(b));default->Objects.equals(String.valueOf(a),String.valueOf(b))||x==y;};}}if(e.equalsIgnoreCase("player is sneaking"))return c.player()!=null&&c.player().isSneaking();if(e.toLowerCase(Locale.ROOT).startsWith("player has permission "))return c.player()!=null&&c.player().hasPermission(unquote(e.substring(22).trim()));if(e.toLowerCase(Locale.ROOT).startsWith("player has item ")){if(c.player()==null)return false;try{return c.player().getInventory().contains(org.bukkit.Material.valueOf(e.substring(16).trim().toUpperCase(Locale.ROOT)));}catch(IllegalArgumentException ex){return false;}}if(e.equalsIgnoreCase("npc is under attack"))return false;return false;}
    private Object resolve(ScriptContext c,String raw){String s=raw.trim();if(s.startsWith("\"")&&s.endsWith("\""))return unquote(s);if(s.equalsIgnoreCase("true")||s.equalsIgnoreCase("false"))return Boolean.parseBoolean(s);try{return Double.parseDouble(s);}catch(NumberFormatException ignored){}String meta=variables.get("meta",c.npc().id(),s,null);if(meta==null)meta=variables.get("meta","",s,null);if(meta!=null){String[] parts=meta.split("\\|",2);String scope=parts[0],owner=parts.length>1?parts[1]:c.npc().id();String value=variables.get(scope,owner,s,null);if(value!=null)return parseStored(value);}return c.value(s);}
    private Object parseStored(String s){if(s.equalsIgnoreCase("true")||s.equalsIgnoreCase("false"))return Boolean.parseBoolean(s);try{return Double.parseDouble(s);}catch(Exception e){return s;}}
    private void createVariable(ScriptContext c,String declaration){String d=declaration.replace("(","").replace(")","").trim();String[] a=d.split("\\s+");if(a.length<2)return;String name=a[0];String value=a[1];String scope=a.length>2?a[2].toLowerCase(Locale.ROOT):"local";String owner=c.npc().id();if(scope.equals("global")){owner="";variables.set("meta","",name,"global|@");}else if(scope.equals("shared")){owner=a.length>3?a[3].replace("[","").replace("]",""):"shared";String[] members=owner.split(",");for(String member:members)variables.set("meta",member.trim(),name,"shared|"+owner);}else{scope="local";variables.set("meta",c.npc().id(),name,"local|"+owner);}variables.set(scope,owner,name,value);c.locals().put(name,parseStored(value));}
    private void storeVariable(ScriptContext c,String name,Object value){String meta=variables.get("meta",c.npc().id(),name,null);if(meta==null)meta=variables.get("meta","",name,"local|"+c.npc().id());String[] parts=meta.split("\\|",2);String scope=parts[0],owner=parts.length>1?parts[1]:c.npc().id();variables.set(scope,owner,name,value);}
    private double num(Object o){try{return Double.parseDouble(String.valueOf(o));}catch(Exception e){return 0;}}
    private String unquote(String s){s=s.trim();return s.length()>=2&&s.startsWith("\"")&&s.endsWith("\"")?s.substring(1,s.length()-1):s;}
    private Player nearest(Location l){return l.getWorld().getPlayers().stream().min(Comparator.comparingDouble(p->p.getLocation().distanceSquared(l))).orElse(null);}
    private Location parseLocation(ScriptContext c,String s){if(s.equals("@p")){Player p=nearest(c.npc().location());return p==null?null:p.getLocation();}if(s.equalsIgnoreCase("location of npc"))return c.npc().location();if(s.startsWith("(")&&s.endsWith(")")){String[] a=s.substring(1,s.length()-1).split(",");if(a.length==3)try{return new Location(c.npc().location().getWorld(),Double.parseDouble(a[0].trim()),Double.parseDouble(a[1].trim()),Double.parseDouble(a[2].trim()));}catch(NumberFormatException ignored){}}return null;}
    private void move(ManagedNPC npc,Location target){Location start=npc.location();if(start.getWorld()!=target.getWorld())return;double distance=start.distance(target);double speed=plugin.getConfig().getDouble("movement.default-speed",4.0);long duration=Math.max(1,Math.round(distance/Math.max(.1,speed)*20));final long[] tick={0};BukkitTask[] holder=new BukkitTask[1];holder[0]=Bukkit.getScheduler().runTaskTimer(plugin,()->{tick[0]++;double t=Math.min(1,(double)tick[0]/duration);Location next=start.clone().add((target.getX()-start.getX())*t,(target.getY()-start.getY())*t,(target.getZ()-start.getZ())*t);next.setYaw((float)(start.getYaw()+(target.getYaw()-start.getYaw())*t));next.setPitch((float)(start.getPitch()+(target.getPitch()-start.getPitch())*t));npc.move(next);if(t>=1){holder[0].cancel();tasks.remove(holder[0]);npc.animate("idle");}},1L,Math.max(1,plugin.getConfig().getLong("movement.update-ticks",1)));tasks.add(holder[0]);npc.animate("walk");}
    private long parseDurationTicks(String s){String[] a=s.trim().split("\\s+");try{long n=Long.parseLong(a[0]);return a.length>1&&a[1].startsWith("second")?n*20:n;}catch(Exception e){return 1;}}
    @EventHandler public void onNpcRightClick(PlayerInteractAtEntityEvent e){
        String id=e.getRightClicked().getScoreboardTags().stream().filter(t->t.startsWith("anpc:")).map(t->t.substring(5)).findFirst().orElse(null);
        if(id!=null){e.setCancelled(true);fire(id,e.getPlayer().isSneaking()?"right click while sneaking":"right click",e.getPlayer());if(!e.getPlayer().getInventory().getItemInMainHand().getType().isAir())fire(id,"item click",e.getPlayer());}
    }
    @EventHandler public void onNpcLeftClick(EntityDamageByEntityEvent e){
        if(!(e.getDamager() instanceof Player p))return;
        String id=e.getEntity().getScoreboardTags().stream().filter(t->t.startsWith("anpc:")).map(t->t.substring(5)).findFirst().orElse(null);
        if(id!=null){e.setCancelled(true);fire(id,p.isSneaking()?"left click while sneaking":"left click",p);fire(id,"damage",p);}
    }
    @EventHandler public void onJoin(PlayerJoinEvent e){for(ManagedNPC npc:npcs.all())if(e.getPlayer().getWorld().equals(npc.location().getWorld())&&e.getPlayer().getLocation().distanceSquared(npc.location())<64*64)npc.show(e.getPlayer());}
    @EventHandler public void onQuit(PlayerQuitEvent e){for(ManagedNPC npc:npcs.all())npc.hide(e.getPlayer());}
    public void shutdown(){tasks.forEach(BukkitTask::cancel);tasks.clear();scripts.clear();nearbyPlayers.clear();}
}
