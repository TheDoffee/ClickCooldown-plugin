package org.thedoffee.clickcooldown;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.material.Lever;
import org.bukkit.material.MaterialData;
import org.bukkit.material.Directional;
import org.bukkit.material.Attachable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {

    private final ClickCooldown plugin;
    private final Map<String, Integer> cooldownTimes = new HashMap<>();
    private final Map<String, BukkitTask> activeCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Long> endTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> pendingRestorations = new HashMap<>();
    private final Map<String, Byte> blockData = new HashMap<>();
    private File cooldownsFile;
    private FileConfiguration cooldownsConfig;

    public CooldownManager(ClickCooldown plugin) {
        this.plugin = plugin;
        this.cooldownsFile = new File(plugin.getDataFolder(), "cooldowns.yml");
        createFile();
    }
    
    private void createFile() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        
        if (!cooldownsFile.exists()) {
            try {
                cooldownsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create cooldowns.yml file: " + e.getMessage());
            }
        }
        
        cooldownsConfig = YamlConfiguration.loadConfiguration(cooldownsFile);
    }
    
    public void loadCooldowns() {
        cooldownTimes.clear();
        pendingRestorations.clear();
        blockData.clear();
        
        ConfigurationSection section = cooldownsConfig.getConfigurationSection("cooldowns");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                int cooldownTime = section.getInt(key);
                cooldownTimes.put(key, cooldownTime);
            }
        }
        
        section = cooldownsConfig.getConfigurationSection("active_cooldowns");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                long remainingTime = section.getLong(key);
                pendingRestorations.put(key, remainingTime);
            }
        }
        
        section = cooldownsConfig.getConfigurationSection("block_data");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                byte data = (byte) section.getInt(key);
                blockData.put(key, data);
            }
        }
    }
    
    public void saveCooldowns() {
        cooldownsConfig.set("cooldowns", null);
        cooldownsConfig.set("active_cooldowns", null);
        cooldownsConfig.set("block_data", null);
        
        for (Map.Entry<String, Integer> entry : cooldownTimes.entrySet()) {
            cooldownsConfig.set("cooldowns." + entry.getKey(), entry.getValue());
        }
        
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : endTimes.entrySet()) {
            String blockKey = entry.getKey();
            long endTime = entry.getValue();
            
            long remainingTime = endTime - currentTime;
            
            if (remainingTime > 0) {
                cooldownsConfig.set("active_cooldowns." + blockKey, remainingTime);
            }
        }
        
        for (Map.Entry<String, Byte> entry : blockData.entrySet()) {
            cooldownsConfig.set("block_data." + entry.getKey(), (int) entry.getValue());
        }
       
        try {
            cooldownsConfig.save(cooldownsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save cooldowns: " + e.getMessage());
        }
    }
    
    public void restoreActivators() {
        if (pendingRestorations.isEmpty()) {
            return;
        }
        
        plugin.getLogger().info("Restoring " + pendingRestorations.size() + " activators...");
        
        for (Map.Entry<String, Long> entry : pendingRestorations.entrySet()) {
            String blockKey = entry.getKey();
            long remainingTime = entry.getValue();
            
            String[] parts = blockKey.split(":");
            if (parts.length != 4) {
                plugin.getLogger().warning("Invalid block key format: " + blockKey);
                continue;
            }
            
            String worldName = parts[0];
            int x, y, z;
            try {
                x = Integer.parseInt(parts[1]);
                y = Integer.parseInt(parts[2]);
                z = Integer.parseInt(parts[3]);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid coordinates in block key: " + blockKey);
                continue;
            }
            
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("World not found: " + worldName);
                continue;
            }
            
            Location location = new Location(world, x, y, z);
            Block block = location.getBlock();
            
            Material blockType = getMaterialTypeFromBlockKey(blockKey);
            if (blockType == null) {
                plugin.getLogger().warning("Could not determine block type for key: " + blockKey);
                blockType = Material.STONE_BUTTON;
            }
            
            byte blockDataValue = 0;
            if (blockData.containsKey(blockKey)) {
                blockDataValue = blockData.get(blockKey);
                plugin.getLogger().info("Found data for block " + blockKey + ": " + blockDataValue);
            } else {
                plugin.getLogger().warning("No data found for block " + blockKey + ", using default value");
            }
            
            final Material finalBlockType = blockType;
            final byte finalBlockDataValue = blockDataValue;
            
            long ticksRemaining = Math.max(1, remainingTime / 50);
            
            plugin.getLogger().info("Scheduled restoration of block " + blockKey + 
                    " (type: " + finalBlockType + ", data: " + finalBlockDataValue + ") in " + ticksRemaining + " ticks");
            
            long endTime = System.currentTimeMillis() + remainingTime;
            endTimes.put(blockKey, endTime);
            
            BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Block currentBlock = location.getBlock();
                
                plugin.getLogger().info("Restoring block " + blockKey + 
                        " (current type: " + currentBlock.getType() + ", new type: " + finalBlockType + ", data: " + finalBlockDataValue + ")");
                
                currentBlock.setType(finalBlockType);
                currentBlock.setData(finalBlockDataValue);
                
                activeCooldowns.remove(blockKey);
                endTimes.remove(blockKey);
                
                plugin.getLogger().info("Block " + blockKey + " successfully restored");
            }, ticksRemaining);
            
            activeCooldowns.put(blockKey, task);
        }
        
        pendingRestorations.clear();
        
        saveCooldowns();
    }
    
    private Material getMaterialTypeFromBlockKey(String blockKey) {
        ConfigurationSection section = cooldownsConfig.getConfigurationSection("block_types");
        if (section != null && section.contains(blockKey)) {
            String materialName = section.getString(blockKey);
            try {
                return Material.valueOf(materialName);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material name: " + materialName);
                return Material.STONE_BUTTON;
            }
        } else {
            plugin.getLogger().warning("Block type not found for key: " + blockKey);
            return Material.STONE_BUTTON;
        }
    }
    
    public void setCooldown(Block block, int seconds) {
        String blockKey = getBlockKey(block);
        cooldownTimes.put(blockKey, seconds);
        
        cooldownsConfig.set("block_types." + blockKey, block.getType().name());
        blockData.put(blockKey, block.getData());
        cooldownsConfig.set("block_data." + blockKey, (int) block.getData());
        
        saveCooldowns();
    }
    
    public void activateCooldown(Block block) {
        activateCooldown(block, block.getState());
    }
    
    public void activateCooldown(Block block, BlockState originalState) {
        String blockKey = getBlockKey(block);
        
        if (!cooldownTimes.containsKey(blockKey)) {
            return;
        }
        
        int cooldownTime = cooldownTimes.get(blockKey);
        Location location = block.getLocation().clone();
        
        MaterialData originalData = originalState.getData().clone();
        Material originalType = originalState.getType();
        byte rawData = originalState.getRawData();
        
        cooldownsConfig.set("block_types." + blockKey, originalType.name());
        blockData.put(blockKey, rawData);
        cooldownsConfig.set("block_data." + blockKey, (int) rawData);
        
        plugin.getLogger().info("Activated cooldown for block " + blockKey + 
                " (type: " + originalType + ", data: " + rawData + ")");
        
        if (originalData instanceof Directional) {
            Directional directional = (Directional) originalData;
            plugin.getLogger().info("Direction: " + directional.getFacing());
        }
        
        if (originalData instanceof Attachable) {
            Attachable attachable = (Attachable) originalData;
            plugin.getLogger().info("Attachment point: " + attachable.getAttachedFace());
        }
        
        block.setType(Material.AIR);
        
        long endTime = System.currentTimeMillis() + (cooldownTime * 1000);
        endTimes.put(blockKey, endTime);
        
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            World world = location.getWorld();
            if (world != null) {
                Block currentBlock = location.getBlock();
                
                currentBlock.setType(originalType);
                currentBlock.setData(rawData);
                
                BlockState newState = currentBlock.getState();
                newState.update(true, false);
                
                plugin.getLogger().info("Restored block " + blockKey + 
                        " (type: " + originalType + ", data: " + rawData + ")");
                
                activeCooldowns.remove(blockKey);
                endTimes.remove(blockKey);
            }
        }, cooldownTime * 20L);
        
        activeCooldowns.put(blockKey, task);
        
        saveCooldowns();
    }
    
    public boolean hasCooldown(Block block) {
        String blockKey = getBlockKey(block);
        return activeCooldowns.containsKey(blockKey);
    }
    
    public boolean hasSetCooldown(Block block) {
        String blockKey = getBlockKey(block);
        return cooldownTimes.containsKey(blockKey);
    }
    
    private String getBlockKey(Block block) {
        Location loc = block.getLocation();
        return loc.getWorld().getName() + ":" + 
               loc.getBlockX() + ":" + 
               loc.getBlockY() + ":" + 
               loc.getBlockZ();
    }
} 