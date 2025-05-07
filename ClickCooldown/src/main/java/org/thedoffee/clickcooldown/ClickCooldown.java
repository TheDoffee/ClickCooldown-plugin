package org.thedoffee.clickcooldown;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.material.Lever;
import org.bukkit.material.PressurePlate;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ClickCooldown extends JavaPlugin implements Listener {

    private CooldownManager cooldownManager;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        cooldownManager = new CooldownManager(this);
        cooldownManager.loadCooldowns();
        cooldownManager.restoreActivators();
    }

    @Override
    public void onDisable() {
        cooldownManager.saveCooldowns();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command is only available to players!");
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("clickcooldown")) {
            if (!player.hasPermission("clickcooldown.use")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }

            if (args.length != 1) {
                player.sendMessage(ChatColor.RED + "Usage: /clickcooldown <seconds>");
                return false;
            }

            Block targetBlock = player.getTargetBlock(null, 5);
            if (targetBlock == null || targetBlock.getType() == Material.AIR) {
                player.sendMessage(ChatColor.RED + "You must be looking at an activator!");
                return true;
            }

            if (!isValidActivator(targetBlock.getType())) {
                player.sendMessage(ChatColor.RED + "Selected block is not an activator!");
                return true;
            }

            int cooldownSeconds;
            try {
                cooldownSeconds = Integer.parseInt(args[0]);
                if (cooldownSeconds <= 0) {
                    player.sendMessage(ChatColor.RED + "Cooldown time must be greater than 0 seconds!");
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Cooldown time must be a whole number!");
                return true;
            }

            cooldownManager.setCooldown(targetBlock, cooldownSeconds);
            player.sendMessage(ChatColor.GREEN + "Cooldown set!");
            
            return true;
        }

        return false;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block clickedBlock = event.getClickedBlock();
        
        if (clickedBlock == null) {
            return;
        }
        
        if (isPressurePlate(clickedBlock.getType())) {
            if (event.getAction() != Action.PHYSICAL) {
                return;
            }
            
            handleActivation(event, clickedBlock);
            return;
        }
        
        if (isButtonOrLever(clickedBlock.getType()) && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            handleActivation(event, clickedBlock);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        checkAndRemoveActivator(event.getBlock());
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        checkAndRemoveActivator(event.getBlock());
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        checkAndRemoveActivators(event.blockList());
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        checkAndRemoveActivators(event.blockList());
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        checkAndRemoveActivator(event.getToBlock());
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        checkAndRemoveActivator(event.getBlock());
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        checkAndRemoveActivator(event.getBlock());
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        checkAndRemoveActivators(event.getBlocks());
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        checkAndRemoveActivators(event.getBlocks());
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        
        if (isValidActivator(block.getType()) && 
            !isValidActivator(event.getChangedType())) {
            cooldownManager.removeBlock(block);
        }
    }
    
    private void handleActivation(PlayerInteractEvent event, Block block) {
        if (cooldownManager.hasCooldown(block)) {
            event.setCancelled(true);
            return;
        }
        
        if (cooldownManager.hasSetCooldown(block)) {
            final BlockState state = block.getState();
            
            Bukkit.getScheduler().runTaskLater(this, () -> {
                cooldownManager.activateCooldown(block, state);
            }, 5L);
        }
    }

    private boolean isPressurePlate(Material material) {
        return material == Material.STONE_PLATE || 
               material == Material.WOOD_PLATE || 
               material == Material.GOLD_PLATE || 
               material == Material.IRON_PLATE;
    }
    
    private boolean isButtonOrLever(Material material) {
        return material == Material.STONE_BUTTON || 
               material == Material.WOOD_BUTTON || 
               material == Material.LEVER;
    }
    
    private boolean isValidActivator(Material material) {
        return isButtonOrLever(material) || isPressurePlate(material);
    }

    private void checkAndRemoveActivator(Block block) {
        if (block != null && isValidActivator(block.getType())) {
            cooldownManager.removeBlock(block);
        }
    }
    
    private void checkAndRemoveActivators(List<Block> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        
        for (Block block : blocks) {
            checkAndRemoveActivator(block);
        }
    }
} 