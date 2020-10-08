/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.draksterau.Regenerator.Handlers;

import com.draksterau.Regenerator.RegeneratorPlugin;
import com.draksterau.Regenerator.integration.Integration;
import com.draksterau.Regenerator.tasks.lagTask;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import net.minecraft.server.v1_15_R1.MinecraftServer;
import net.minecraft.server.v1_15_R1.WorldServer;
import net.minecraft.server.v1_15_R1.EntityPlayer;
import org.bukkit.craftbukkit.v1_15_R1.CraftServer;
import net.minecraft.server.v1_15_R1.PlayerInteractManager;
import org.bukkit.craftbukkit.v1_15_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_15_R1.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;


/**
 *
 * @author draks
 */
public class RUtils extends RObject {

    public static HashMap<Location, Boolean> breakAndResult = new HashMap<Location, Boolean>();
    
    public RUtils(RegeneratorPlugin plugin) {
        super(plugin);
    }
    
    // Moves offline players on a chunk to the spawn of the world.
    public void moveOfflinePlayers(Chunk chunk) {
        Entity[] entities = chunk.getEntities();
        List<Player> players = new ArrayList<Player>();
        for (Entity entity : entities) {
            if (entity instanceof Player) {
                Player player = (Player)entity;
                if (!player.isOnline()) {
                    player.teleport(chunk.getWorld().getSpawnLocation());
                    players.add(player);
                }
            }
        }
        tellPlayersOnWorld(chunk.getWorld(), "The following players have been moved to the world spawn for world: " + chunk.getWorld().getName() + ": " + players.toString());
    }
    // Gets an RWorld
    public RWorld getRWorldForWorld(World world) {
        for (RWorld RWorld : plugin.loadedWorlds) {
            if (RWorld.world.equals(world)) {
                RWorld.loadData();
                return RWorld;
            }
        }
        return null;
    }
    
    public boolean uuidInUse(UUID id) {
        return Bukkit.getPlayer(id) != null || Bukkit.getOfflinePlayer(id) != null;
    }
    
    public Player getFakePlayer(){
        if (plugin.fakePlayer == null) {
            MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
            WorldServer world = ((CraftWorld) Bukkit.getWorlds().get(0)).getHandle();
            EntityPlayer npc = new EntityPlayer(server, world, new GameProfile(plugin.config.fakePlayerUUID, "Regenerator"), new PlayerInteractManager(world));
            CraftPlayer player = new CraftPlayer((CraftServer)Bukkit.getServer(), npc);
            plugin.fakePlayer = player;
        }
        return plugin.fakePlayer;
    }
    
    public boolean canBreakChunk(Chunk c) {
            int X = c.getX() * 16;
            int Z = c.getZ() * 16;
            int Y = c.getWorld().getHighestBlockAt(X,Z).getY() +1;
            if (!canBreakAt(new Location(c.getWorld(), X,Y,Z))) return false;
//            
//            for (int x = 0; x < 16; x++) {
//                for (int z = 0; z < 16; z++) {
//                    for (int y = 0; y < 256; y++) {
//                        if (!canBreakAt(new Location(c.getWorld(),X+x,y,Z+z))) return false;
//                    }
//                }
//            }
            return true;
    }
    
    public boolean canBreakAt(Location loc) {
        BlockBreakEvent event = new BlockBreakEvent(loc.getBlock(),getFakePlayer());
        breakAndResult.put(loc, true);
        Bukkit.getServer().getPluginManager().callEvent(event);
        boolean result = breakAndResult.get(loc);
        breakAndResult.remove(loc);
        return result;
    }
    // Returns a formatted string of Enabled or disabled
    
    public String getStatusForBoolean(boolean bool) {
        if (bool) return ChatColor.GREEN + "Enabled" + ChatColor.GRAY;
        return ChatColor.RED + "Disabled" + ChatColor.GRAY;
    }

    public boolean regenerateChunk(Chunk chunk) {
        int bx = chunk.getX() << 4;
        int bz = chunk.getZ() << 4;
        try {
            com.sk89q.worldedit.math.BlockVector3 pt1 =  com.sk89q.worldedit.math.BlockVector3.at(bx, 0, bz);
            com.sk89q.worldedit.math.BlockVector3 pt2 = com.sk89q.worldedit.math.BlockVector3.at(bx + 15, 256, bz + 15);
            com.sk89q.worldedit.bukkit.BukkitWorld world = new com.sk89q.worldedit.bukkit.BukkitWorld(chunk.getWorld());
            com.sk89q.worldedit.regions.CuboidRegion region = new com.sk89q.worldedit.regions.CuboidRegion(world, pt1, pt2);   
            com.sk89q.worldedit.EditSession session = com.sk89q.worldedit.WorldEdit.getInstance().getEditSessionFactory().getEditSession(world, 65536);
            boolean result = world.regenerate(region, session);
            session.flushSession();
            return result;
        } catch (Exception e) {
            plugin.utils.throwMessage(MsgType.SEVERE, String.format(plugin.lang.getForKey("messages.regenFailedWorldEdit"), bx, bz, chunk.getWorld().getName(), e.getMessage()));
            if (plugin.config.debugMode) e.printStackTrace();
            return false;
        }
    }
    // This verifies a chunk is inactive and unclaimed.
    public boolean autoRegenRequirementsMet(Chunk chunk) {
        
        RWorld RWorld = getRWorldForWorld(chunk.getWorld());
        
        // If the world is not loaded, do nothing.
        if (RWorld == null) {
            plugin.utils.throwMessage(MsgType.DEBUG, "Not allowing autoregen of chunk: " + chunk.getX() + "," + chunk.getZ() + " on world: " + chunk.getWorld().getName() + " as the world is not correctly initialised. Report this  to Devs!");
            return false;
        }
        // Blocked at the world level.
        if (!RWorld.canAutoRegen()) {
            plugin.utils.throwMessage(MsgType.DEBUG, "Not allowing autoregen of chunk: " + chunk.getX() + "," + chunk.getZ() + " on world: " + chunk.getWorld().getName() + " as the world has autoregen disabled.");
            return false;
        }
                
        // This handles the world configuration for borders and the skip radius.
        if (RWorld.minBlockAutoRegen >= distance(chunk.getX(), 100.0, chunk.getZ(), chunk.getWorld().getSpawnLocation().getBlockX(), 100.0, chunk.getWorld().getSpawnLocation().getBlockZ())) return false;
        if (RWorld.maxBlockAutoRegen != 0 && RWorld.maxBlockAutoRegen > distance(chunk.getX(), 100.0, chunk.getZ(), chunk.getWorld().getSpawnLocation().getBlockX(), 100.0, chunk.getWorld().getSpawnLocation().getBlockZ())) return false;
        // Blocked at the integration level.
        for (Integration integration : plugin.loadedIntegrations) {
            if (!integration.shouldChunkAutoRegen(chunk)) {
                plugin.utils.throwMessage(MsgType.DEBUG, "Not allowing autoregen of chunk: " + chunk.getX() + "," + chunk.getZ() + " on world: " + chunk.getWorld().getName() + " as " + integration.getPluginName() + " is preventing it.");
                return false;
            }
        }
        if (plugin.config.enableUnknownProtectionDetection && !plugin.utils.canBreakChunk(chunk)) {
            plugin.utils.throwMessage(MsgType.DEBUG, "Not allowing autoregen of chunk: " + chunk.getX() + "," + chunk.getZ() + " on world: " + chunk.getWorld().getName() + " as an unknown plugin is preventing it.");
            return false;
        }
        // Not blocked.
        return true;
    }
    // This simply lists supported plugins and versions.
    public void iterateIntegrations() {
        for (List<String> integration : plugin.availableIntergrations) {
            String name = integration.get(2).replace("Integration", "");
            throwMessage(MsgType.INFO, ChatColor.LIGHT_PURPLE + name);
        }
    }
    
    public ArrayList<RChunk> getRChunksNear(Location loc, int radiusChunks) {
        ArrayList<RChunk> chunks = new ArrayList<RChunk>();
        int blockRadius = radiusChunks * 16;
        for (int x = (int) ((loc.getX() - blockRadius)); x <= loc.getX() + blockRadius;x += 16) {
            for (int z = (int)(loc.getZ() - blockRadius);z <= loc.getZ() + blockRadius;z += 16) {
                Location location = new Location(loc.getWorld(),x,0,z);
                RChunk RChunk = new RChunk(plugin, location.getChunk().getX(), location.getChunk().getZ(), location.getWorld().getName());
                chunks.add(RChunk);
            }
        }
        return chunks;
    }
    
    public String getEnglishTimeFromMs(Long ms) {
        if (ms < 1000) return "Now";
        if (ms == null) return "Never";
        Long seconds = ms / 1000;
        int days = 0;
        int hours = 0;
        int minutes = 0;
        while (seconds >= 86400) {
            days += 1;
            seconds -= 86400;
        }
        while (seconds >= 3600) {
            hours += 1;
            seconds -= 3600;
        }
        while (seconds >= 60) {
            minutes += 1;
            seconds -= 60;
        }
        String returnValue = "";
        if (days > 0) {
            returnValue += (days + " days, ");
        }
        if (hours > 0) {
            returnValue += (hours + " hrs, ");            
        }
        if (minutes > 0) {
            returnValue += (minutes + " mins, ");            
        }
        returnValue += (seconds + " seconds");
        return returnValue;
    }
    public void printErrorReport(String error) {
        throwMessage(MsgType.INFO, ChatColor.RED + "A Severe error has been encountered (" + error + "). Please consider submitting this on github at https://github.com/Bysokar/Regenerator/Issues" + ChatColor.RESET);
        throwMessage(MsgType.INFO, "Please be sure to include the following:");
        throwMessage(MsgType.INFO, "Bukkit Server Version: " + Bukkit.getVersion() + ", implementing Bukkit API version: " + Bukkit.getBukkitVersion());
        throwMessage(MsgType.INFO, "Regenerator version: v" + this.plugin.getDescription().getVersion());
        //TODO: Check WorldEdit version and add here.
        if (Bukkit.getPluginManager().isPluginEnabled("WorldEdit")) {
            throwMessage(MsgType.INFO, "WorldEdit version: v" +  Bukkit.getPluginManager().getPlugin("WorldEdit").getDescription().getVersion());
        } else {
            throwMessage(MsgType.INFO, "WorldEdit version: None (You must install WorldEdit for Regenerator to work, please dont report this!)");
        }
        for (Integration i : plugin.loadedIntegrations) {
            throwMessage(MsgType.INFO, "Integration " + ChatColor.GREEN + "enabled" + ChatColor.DARK_AQUA + " for: " + ChatColor.GOLD + i.getPluginName() + " v" + i.getPluginVersion() + "");
        }
        throwMessage(MsgType.INFO, ChatColor.RED + "Please also include a link to your configuration, as well as any log files which may be relavent." + ChatColor.RESET);
        throwMessage(MsgType.INFO, ChatColor.RED + "Please ensure you reproduce the error with debug mode enabled to capture all details in logs first!" + ChatColor.RESET);
        
    }
    
    // Formats a message and categorises it instead of using logger directly.
    public  void throwMessage(MsgType type, String message) {

        ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
        if (MsgType.INFO.equals(type)) {
            console.sendMessage(getFancyName() + ChatColor.DARK_AQUA + "[" + type.name() + "]: " + message);
        } else {
            if (MsgType.WARNING.equals(type)) {
                console.sendMessage(getFancyName() + ChatColor.YELLOW + "[" + type.name() + "]: " + message);
            } else {
                if (MsgType.SEVERE.equals(type)) {
                    console.sendMessage(getFancyName() + ChatColor.RED + "[" + type.name() + "]: " + message);
                    printErrorReport(message);
                } else {
                    if (MsgType.NEW.equals(type)) {
                        console.sendMessage(getFancyName() + ChatColor.LIGHT_PURPLE + "[" + type.name() + "]: " + message);
                    } else {
                        if (MsgType.SUCCESS.equals(type)) {
                            console.sendMessage(getFancyName() + ChatColor.GREEN + "[" + type.name() + "]: " + message);
                        } else {
                            if (MsgType.DEBUG.equals(type) && plugin.config.debugMode) {
                                console.sendMessage(getFancyName() + ChatColor.DARK_RED + "[" + type.name() + "]: " + message);
                            } else {
                                if (!MsgType.DEBUG.equals(type)) this.throwMessage(MsgType.SEVERE,String.format(this.plugin.lang.getForKey("messages.errorThrowingMessage")));
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Tells all players on a world, the specified message.
    public void tellPlayersOnWorld(World world, String message) {
        List<Entity> entities = world.getEntities();
        for (Entity entity : entities) {
            if (entity instanceof Player) {
                Player player = (Player)entity;
                if (player.isOnline() && !player.isOp() && !player.hasPermission("regenerator.notify")) {
                    player.sendMessage(getFancyName() + " " + message);
                }                
            }
        }
    }
    
    // Returns a formatted version of the plugins name.
    public String getFancyName() {
        return ChatColor.RED + "[" + ChatColor.DARK_GREEN + plugin.getDescription().getName() + ChatColor.RED + "] " + ChatColor.GRAY;
    }
    
    // Gets the players nearby to a chunk based on a distance value.
    public List<Player> getPlayersNearChunk(RChunk rChunk, int distance) {
        List<Player> playersNearby = new ArrayList<Player>();
        List<Player> playersOnWorld = rChunk.getChunk().getWorld().getPlayers();
        for (Player p : playersOnWorld) {
            // Skip the player if they are further away.
            if (distance(p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ(), rChunk.getChunk().getX(), p.getLocation().getBlockY(), rChunk.getChunk().getZ()) > distance) continue; 
            playersNearby.add(p);
        }
        return playersNearby;
    }
    
    
    // This will be used for ingame debugging.
    public void sendNotifyMessage(String message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasPermission("regenerator.notify")) continue;
            p.sendMessage(getFancyName() + " " + ChatColor.GRAY + message);
        }
    }
    
    // Gets the distance between to X,Y,Z coordinates.
    public double distance(double sx, double sy, double sz, double dx, double dy, double dz) {
        double distance = Math.sqrt(Math.pow(sx-dx,2) + Math.pow(sx-dx,2) + Math.pow(sz-dz,2));
        return distance;
    }
    // Gets the count of online players in a chunk.
    public int onlinePlayersInChunk(Chunk chunk) {
        int count = 0;
        Entity[] entities = chunk.getEntities();
        for (Entity entity : entities) {
            if (entity instanceof Player) {
                Player player = (Player)entity;
                if (player.isOnline()) {
                    count++;
                }
            }
        }
        return count;
    }

    // Checks a chunk for an integration (claim) on the chunk
    public Integration getIntegrationForChunk(Chunk chunk) {
        for (Integration integration : plugin.loadedIntegrations) {
            if (integration.isChunkClaimed(chunk)) {
                return integration;
            }
        }
        return null;
    }
    // Checks for the number of integrations (claim plugins) that are claiming a chunk
    public int getCountIntegration(Chunk chunk) {
        int count = 0;
        for (Integration integration : plugin.loadedIntegrations) {
            if (integration.isChunkClaimed(chunk)) {
                count++;
            }
        }
        return count;
    }
    

    public Chunk getSenderChunk(CommandSender sender) {
        if (sender instanceof Player) {
            Player player = (Player)sender;
            return player.getLocation().getChunk(); 
        } else {
            return null;
        }
    }
    public Player getSenderPlayer(CommandSender sender) {
        if (sender instanceof Player) {
            Player player = (Player)sender;
            return player;
        } else {
            return null;
        }
    }
    
    public boolean canManuallyRegen(Player player, Chunk chunk) {
        
        RWorld RWorld = getRWorldForWorld(chunk.getWorld());
        
        // If the world is not loaded, do nothing.
        if (RWorld == null) {
            plugin.utils.throwMessage(MsgType.DEBUG, "Denying player: " + player.getName() + " rights to regenerate as world: " + chunk.getWorld().getName() + " is not initialised in Regenrator. Report this to Devs!");
            return false;
        }
        
        // If the world has manual regen disabled, do not allow it.
        if (!RWorld.canManualRegen()) {
            plugin.utils.throwMessage(MsgType.DEBUG, "Denying player: " + player.getName() + " rights to regenerate as world: " + chunk.getWorld().getName() + " has manual regen disabled.");
            return false;
        }
        
        // This returns true if the player has the override permission node but only for claimed land.
        if (player.hasPermission("regenerator.regen.override") && getIntegrationForChunk(chunk) != null) {
            plugin.utils.throwMessage(MsgType.DEBUG, "Denying player: " + player.getName() + " rights to regenerate as they do not have regenerator.regen.override permission.");
            return true;
        }

        
        // Is it unclaimed?
        if (getIntegrationForChunk(chunk) == null) {
            if (player.hasPermission("regenerator.regen.unclaimed")) {
                return true;
            } else {
                plugin.utils.throwMessage(MsgType.DEBUG, "Denying player: " + player.getName() + " rights to regenerate as they do not have regenerator.regen.unclaimed permission.");
            }
        }
        
        // Blocked at the integration level.
        for (Integration integration : plugin.loadedIntegrations) {
            if (!integration.canPlayerRegen(player,chunk)) {
                plugin.utils.throwMessage(MsgType.DEBUG, "Denying player: " + player.getName() + " rights to regenerate as integration with : " + integration.getPluginName() + " is demanding the " + integration.getPermissionRequiredToRegen(player, chunk) + " permission node, which they do not have.");
                return false;
            }
        }
        
        return true;
    }
    
    public void loadWorlds() {
        for (World world : Bukkit.getWorlds()) {
            throwMessage(MsgType.INFO, String.format(this.plugin.lang.getForKey("messages.loadingWorld"), world.getName()));
            RWorld RWorld = new RWorld(plugin, world);
            RChunk RChunk = new RChunk(plugin, world.getSpawnLocation().getBlockX(), world.getSpawnLocation().getBlockX(), world.getName());
            plugin.loadedWorlds.add(RWorld);
            throwMessage(MsgType.INFO, String.format(this.plugin.lang.getForKey("messages.loadedWorld"), world.getName()));
        }
    }
    public void initAvailableIntegrations() {
        List<String> GriefPrevention = new ArrayList<String>();
        GriefPrevention.add("GriefPrevention");
        GriefPrevention.add("16");
        GriefPrevention.add("GriefPreventionIntegration");
        plugin.availableIntergrations.add(GriefPrevention);
    }
    
    public boolean isLagOK() {
     if (lagTask.getTps() >= plugin.config.minTpsRegen) {
         return true;
     } else {
         return false;
     }
    }
    
    public void clearEntitiesFromChunk (RChunk rChunk) {
        int count = 0;
        for (Entity e : rChunk.getChunk().getEntities()) {
            if (plugin.config.excludeEntityTypesFromRegeneration.contains(e.getType().getName())) continue;
            if (e instanceof Player) continue;
            e.remove();
            count++;
        }   
        throwMessage(MsgType.DEBUG, String.format(plugin.lang.getForKey("messages.entitiesRemovedCount"), count,rChunk.chunkX, rChunk.chunkZ, rChunk.getWorldName()));
    }
    
    public boolean validateChunkInactivity (RChunk rChunk) {
        
        long secSinceLastActive = 0;
        
        RWorld RWorld = getRWorldForWorld(rChunk.getWorld());       
        
        // IF the chunk doesnt exist, do nothing.
        if (rChunk == null) return false;
        
        if (rChunk.lastActivity != 0 && rChunk.lastActivity != -1) {
            secSinceLastActive = (System.currentTimeMillis() - rChunk.lastActivity) / 1000;
        }
        
        // If the chunk has never been modified, dont do anything.
        if (secSinceLastActive == 0) return false;

        if (secSinceLastActive < RWorld.getIntervalSecs()) return false;

        return true;
    }
    
    public Integration getLoadedIntegration(String name) {
        for (Integration integration : plugin.loadedIntegrations) {
            if (integration.getPluginName().equals(name)) {
                return integration;
            }
        }
        return null;
    }
    
    public void loadIntegrationFor(List<String> plugins) {
        String[] module = plugins.toArray(new String[plugins.size()]);
        try {
            if (Bukkit.getPluginManager().isPluginEnabled(module[0])) {
                if (Bukkit.getPluginManager().getPlugin(module[0]).getDescription().getVersion().startsWith(module[1])) {
                    Class<?> integrationClass = Class.forName("com.draksterau.Regenerator.integration." + module[2]);
                    if (Integration.class.isAssignableFrom(integrationClass)) {
                        Integration integration = (Integration) integrationClass.newInstance();
                        integration.plugin = module[0];
                        integration.RegeneratorPlugin = plugin;                        
                        throwMessage(MsgType.INFO, "[" + module[2] + "] " + String.format(plugin.lang.getForKey("messages.detectedAndLoadingIntegration"), integration.getPluginName(), integration.getPluginVersion(), module[2]));
                        integration.validateConfig();
                        plugin.loadedIntegrations.add(integration);
                        if (!integration.supportsUnknownProtectionDetection() && plugin.config.enableUnknownProtectionDetection) {
                            throwMessage(MsgType.WARNING, String.format(plugin.lang.getForKey("messages.integrationConflictFound"), integration.getPluginName()));
                            plugin.config.enableUnknownProtectionDetection = false;
                            plugin.config.saveData();
                        }
                    }
                } else {
                    throwMessage(MsgType.WARNING, "[" + module[2] + "] " + String.format(plugin.lang.getForKey("messages.detectedInvalidVersionForIntegration"), module[0], Bukkit.getPluginManager().getPlugin(module[0]).getDescription().getVersion(), module[1]));
                    throwMessage(MsgType.WARNING, String.format(plugin.lang.getForKey("messages.disableIntegration"), module[2]));
                }
            }
        } catch (ClassNotFoundException ex) {
            throwMessage(MsgType.SEVERE, String.format(plugin.lang.getForKey("messages.integrationUnsupported"), plugin));
        } catch (InstantiationException | IllegalAccessException ex) {
            throwMessage(MsgType.SEVERE, String.format(plugin.lang.getForKey("messages.integrationUnsupportedShouldBe"), plugin));
            if (plugin.config.debugMode) ex.printStackTrace();
        }
        
    }
    
    public List<String> convertToModule(String pluginToUse) {
        String name = Bukkit.getPluginManager().getPlugin(pluginToUse).getName();
        String version = Bukkit.getPluginManager().getPlugin(pluginToUse).getDescription().getVersion();
        for (List<String> module : plugin.availableIntergrations) {
            if (module.get(0).equals(name) && version.startsWith(module.get(1))) {
                return module;
            }
        }
        return null;
    }
    public boolean isEnabledIntegration(List<String> pluginToUse) {
        for (Integration integration : plugin.loadedIntegrations) {
            if (integration.getPluginName().equals(pluginToUse.get(0)) && integration.getPluginVersion().startsWith(pluginToUse.get(1))) {
                return true;
            }
        }
        return false;
    }
    
    public void disableIntegrationFor(List<String> PluginToDisable) {
        Integration toDisable = null;
        for (Integration integration : plugin.loadedIntegrations) {
            if (integration.getPluginName().equals(PluginToDisable.get(0)) && integration.getPluginVersion().startsWith(PluginToDisable.get(1))) {
                toDisable = integration;
            }
        }
        if (toDisable != null) {
            plugin.loadedIntegrations.remove(toDisable);
        }
    }
    public void loadIntegrations() {
        for (List<String> p : plugin.availableIntergrations) {
            loadIntegrationFor(p);
        }
    }
    
    public long convertMsToSecond(long newMS, long oldMS) {
        return ((newMS - oldMS) / 1000);
    }
    
    @Override
    void loadData() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    void saveData() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
