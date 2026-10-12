package net.viraxis.supplycrates.manager;

import com.massivecraft.massivecore.Engine;
import com.massivecraft.massivecore.util.Feedback;
import com.massivecraft.massivecore.util.ActionFeedback;
import com.massivecraft.massivecore.util.SoundBuilder;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.viraxis.holograms.HologramsPlugin;
import net.viraxis.supplycrates.SupplyCratesPlugin;
import net.viraxis.supplycrates.entity.SupplyCratesConf;
import net.viraxis.supplycrates.entity.SupplyReward;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Firework;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class SupplyCrateManager extends Engine {
    private static final SupplyCrateManager i = new SupplyCrateManager();
    private static final String HOLOGRAM_ID = "supply_crate_active";
    private final Random random = new Random();
    private final Map<UUID, Long> hitCooldowns = new HashMap<>();
    private Location location;
    private BlockData replaced;
    private int hitsRemaining;
    private Instant expiresAt;
    private Instant nextSpawnAt;
    private long nextSignalAt;
    private FallingBlock falling;
    private BukkitTask descentTask;

    public static SupplyCrateManager get() { return i; }
    private SupplyCrateManager() { setPeriod(20L); setSync(true); }

    @Override public void run() {
        SupplyCratesConf conf = SupplyCratesConf.get();
        if (conf == null || !conf.enabled) return;
        Instant now = Instant.now();
        if (location == null) {
            if (nextSpawnAt == null) scheduleNext();
            if (!now.isBefore(nextSpawnAt) && Bukkit.getOnlinePlayers().size() >= conf.minimumPlayers) spawn(false);
            return;
        }
        if (falling != null && falling.isValid()) {
            falling.getWorld().spawnParticle(Particle.SMOKE, falling.getLocation(), 10, .2, .2, .2, .02);
        } else if (falling == null) {
            location.getWorld().spawnParticle(Particle.SMOKE, location.clone().add(.5, .55, .5), 20, .25, .25, .25, .05);
        }
        if (expiresAt != null && !now.isBefore(expiresAt)) {
            broadcastAnnouncement(conf.expiredAnnouncement, Sound.BLOCK_END_PORTAL_SPAWN, .65F, .75F, null);
            remove(true);
            return;
        }
        if (conf.signalSeconds > 0 && System.currentTimeMillis() >= nextSignalAt) {
            signal();
            nextSignalAt = System.currentTimeMillis() + conf.signalIntervalSeconds * 1000L;
        }
    }

    public boolean spawn(boolean forced) {
        if (location != null) return false;
        SupplyCratesConf conf = SupplyCratesConf.get();
        Location chosen = chooseLocation(conf);
        if (chosen == null) {
            SupplyCratesPlugin.get().getLogger().warning("Could not find a safe surface inside WorldGuard region '" + conf.region + "'.");
            scheduleNext();
            return false;
        }
        Block block = chosen.getBlock();
        this.location = block.getLocation();
        this.replaced = block.getBlockData().clone();
        this.hitsRemaining = conf.requiredHits;
        this.expiresAt = Instant.now().plus(Duration.ofMinutes(conf.lifetimeMinutes));
        this.nextSignalAt = 0L;
        if (conf.useFallingBlock) {
            this.falling = block.getWorld().spawnFallingBlock(chosen.clone().add(.5, conf.fallingHeight, .5),
                    conf.fallingMaterial.createBlockData());
            this.falling.setDropItem(false);
            this.falling.setHurtEntities(false);
            this.falling.setGravity(false);
            this.falling.setGlowing(true);
            animateDrop();
        } else {
            land();
        }
        broadcastAnnouncement(conf.spawnAnnouncement, Sound.ENTITY_ENDER_DRAGON_GROWL, .65F, 1.35F, null);
        return true;
    }

    public void remove(boolean schedule) {
        if (descentTask != null) { descentTask.cancel(); descentTask = null; }
        if (falling != null) { falling.remove(); falling = null; }
        if (location != null && location.getWorld() != null) {
            Block block = location.getBlock();
            if (block.getType() == SupplyCratesConf.get().crateMaterial && replaced != null) block.setBlockData(replaced, false);
            for (Player viewer : location.getWorld().getPlayers()) viewer.sendBlockDamage(location, 0F);
        }
        try { HologramsPlugin.get().getHolograms().remove(HOLOGRAM_ID); } catch (Throwable ignored) {}
        location = null; replaced = null; hitsRemaining = 0; expiresAt = null; hitCooldowns.clear();
        if (schedule) scheduleNext();
    }

    public void shutdown() { remove(false); }
    public boolean active() { return location != null; }
    public int hitsRemaining() { return hitsRemaining; }
    public Instant nextSpawnAt() { return nextSpawnAt; }
    public Location location() { return location == null ? null : location.clone(); }

    private void scheduleNext() {
        SupplyCratesConf conf = SupplyCratesConf.get();
        int spread = Math.max(0, conf.maximumIntervalMinutes - conf.minimumIntervalMinutes);
        int minutes = conf.minimumIntervalMinutes + (spread == 0 ? 0 : random.nextInt(spread + 1));
        nextSpawnAt = Instant.now().plus(Duration.ofMinutes(minutes));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(PlayerInteractEvent event) {
        if (location == null || event.getAction() != Action.LEFT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) return;
        if (!same(event.getClickedBlock().getLocation(), location)) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        long now = System.currentTimeMillis();
        long last = hitCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < SupplyCratesConf.get().hitCooldownMillis) return;
        hitCooldowns.put(player.getUniqueId(), now);
        hitsRemaining--;
        float damage = 1F - (hitsRemaining / (float) Math.max(1, SupplyCratesConf.get().requiredHits));
        for (Player viewer : location.getWorld().getPlayers()) {
            if (viewer.getLocation().distanceSquared(location) <= 96D * 96D) viewer.sendBlockDamage(location, Math.clamp(damage, 0F, .99F));
        }
        location.getWorld().spawnParticle(Particle.CRIT, location.clone().add(.5, .8, .5), 8, .35, .35, .35, .08);
        player.playSound(location, Sound.BLOCK_WOOD_HIT, .8f, 1.15f + random.nextFloat() * .35f);
        if (hitsRemaining <= 0) claim(player); else updateHologram();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (location == null) return;
        UUID eventId = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskLater(SupplyCratesPlugin.get(), () -> {
            Player player = Bukkit.getPlayer(eventId);
            if (player == null || !player.isOnline() || location == null) return;
            sendAnnouncement(player, SupplyCratesConf.get().joinReminder,
                    new SoundBuilder(Sound.BLOCK_NOTE_BLOCK_BELL, .8F, 1.25F), null);
        }, 40L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFallingLand(EntityChangeBlockEvent event) {
        if (falling == null || !event.getEntity().getUniqueId().equals(falling.getUniqueId())) return;
        event.setCancelled(true);
        falling.remove(); falling = null;
        land();
    }

    private void animateDrop() {
        SupplyCratesConf conf = SupplyCratesConf.get();
        Particle.DustOptions blue = new Particle.DustOptions(Color.fromRGB(60, 155, 255), 1.35F);
        this.descentTask = Bukkit.getScheduler().runTaskTimer(SupplyCratesPlugin.get(), () -> {
            if (falling == null || !falling.isValid() || location == null) {
                if (descentTask != null) descentTask.cancel();
                descentTask = null;
                return;
            }
            Location current = falling.getLocation();
            double landingY = location.getY() + .05D;
            if (current.getY() <= landingY + conf.fallingBlocksPerTick) {
                falling.remove(); falling = null;
                descentTask.cancel(); descentTask = null;
                land();
                return;
            }
            Location next = current.subtract(0D, conf.fallingBlocksPerTick, 0D);
            falling.teleport(next);
            World world = next.getWorld();
            world.spawnParticle(Particle.DUST, next.clone().add(0, .5, 0), 5, .35, .35, .35, 0D, blue);
            world.spawnParticle(Particle.CLOUD, next.clone().add(0, .25, 0), 2, .18, .08, .18, .01);
            world.spawnParticle(Particle.DUST, location.clone().add(.5, 1.5, .5), 4, .12, 1.5, .12, 0D, blue);
        }, 1L, 1L);
    }

    private void land() {
        if (location == null) return;
        location.getBlock().setType(SupplyCratesConf.get().crateMaterial, false);
        location.getWorld().playSound(location, Sound.BLOCK_STONE_BREAK, 1f, .85f);
        location.getWorld().spawnParticle(Particle.BLOCK, location.clone().add(.5, .5, .5), 35, .45, .45, .45, .08,
                SupplyCratesConf.get().crateMaterial.createBlockData());
        updateHologram();
        signal();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        if (location != null && same(event.getBlock().getLocation(), location)) event.setCancelled(true);
    }
    @EventHandler public void onExplode(EntityExplodeEvent event) { protect(event.blockList()); }
    @EventHandler public void onExplode(BlockExplodeEvent event) { protect(event.blockList()); }
    private void protect(List<Block> blocks) { if (location != null) blocks.removeIf(block -> same(block.getLocation(), location)); }

    private void claim(Player player) {
        Location effect = location.clone().add(.5, .5, .5);
        award(player);
        firework(effect, 0);
        effect.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, effect, 60, .7, 1, .7, .15);
        effect.getWorld().playSound(effect, Sound.ENTITY_PLAYER_LEVELUP, 1.2f, 1.15f);
        broadcastAnnouncement(SupplyCratesConf.get().claimedAnnouncement, Sound.ENTITY_FIREWORK_ROCKET_BLAST, .9F, 1.1F, player.getName());
        remove(true);
    }

    private void award(Player player) {
        List<SupplyReward> valid = SupplyCratesConf.get().rewards.stream().filter(r -> r != null && r.weight > 0).toList();
        for (int roll = 0; roll < SupplyCratesConf.get().rewardRolls && !valid.isEmpty(); roll++) {
            SupplyReward reward = weighted(valid);
            int amount = Math.max(1, reward.minimumAmount) + random.nextInt(Math.max(1, reward.maximumAmount - Math.max(1, reward.minimumAmount) + 1));
            if (reward.item != null) {
                ItemStack item = reward.item.clone().build();
                item.setAmount(Math.min(item.getMaxStackSize(), amount));
                if (SupplyCratesConf.get().dropRewardsAtCrate && location != null) {
                    location.getWorld().dropItemNaturally(location.clone().add(.5, .75, .5), item);
                } else {
                    Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
                    overflow.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                }
            }
            for (String command : reward.commands == null ? List.<String>of() : reward.commands) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("<player>", player.getName()).replace("%player%", player.getName()));
            }
        }
    }

    private SupplyReward weighted(List<SupplyReward> rewards) {
        double total = rewards.stream().mapToDouble(reward -> reward.weight).sum();
        double selected = random.nextDouble() * total;
        for (SupplyReward reward : rewards) { selected -= reward.weight; if (selected <= 0) return reward; }
        return rewards.getLast();
    }

    private void updateHologram() {
        SupplyCratesConf conf = SupplyCratesConf.get();
        List<String> text = conf.hologram.stream().map(line -> line.replace("<hits>", String.valueOf(hitsRemaining))).toList();
        HologramsPlugin.get().getHolograms().createRuntimeText(HOLOGRAM_ID,
                location.clone().add(.5, 1.65, .5), text, 64, true, false);
    }

    private void signal() {
        if (location == null) return;
        Location at = location.clone().add(.5, .2, .5);
        at.getWorld().spawnParticle(Particle.END_ROD, at, 28, .25, 2.5, .25, .02);
        at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 1.5, 0), 35, .3, 2.5, .3, 0D,
                new Particle.DustOptions(Color.fromRGB(60, 155, 255), 1.5F));
        firework(at, 1);
    }

    private void firework(Location at, int power) {
        Firework firework = at.getWorld().spawn(at, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.ORANGE, Color.YELLOW, Color.WHITE).trail(true).build());
        meta.setPower(power); firework.setFireworkMeta(meta);
        if (power == 0) Bukkit.getScheduler().runTaskLater(SupplyCratesPlugin.get(), firework::detonate, 2L);
    }

    private Location chooseLocation(SupplyCratesConf conf) {
        World world = Bukkit.getWorld(conf.world);
        if (world == null) return null;
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        ProtectedRegion region = manager == null ? null : manager.getRegion(conf.region);
        if (region == null) return null;
        BlockVector3 min = region.getMinimumPoint(), max = region.getMaximumPoint();
        for (int attempt = 0; attempt < conf.locationAttempts; attempt++) {
            int x = randomBetween(min.x(), max.x());
            int z = randomBetween(min.z(), max.z());
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
            if (!region.contains(x, y, z)) continue;
            Block target = world.getBlockAt(x, y, z);
            if (!safeSurface(world, region, target, conf)) continue;
            return target.getLocation();
        }
        return null;
    }

    private boolean safeSurface(World world, ProtectedRegion region, Block center, SupplyCratesConf conf) {
        Material centerGround = center.getRelative(0, -1, 0).getType();
        if (unsafeGround(centerGround)) return false;
        int centerY = center.getY();
        for (int dx = -conf.safeSurfaceRadius; dx <= conf.safeSurfaceRadius; dx++) {
            for (int dz = -conf.safeSurfaceRadius; dz <= conf.safeSurfaceRadius; dz++) {
                int x = center.getX() + dx, z = center.getZ() + dz;
                int surfaceY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
                if (Math.abs(surfaceY - centerY) > conf.maximumNearbySlope || !region.contains(x, surfaceY, z)) return false;
                Block feet = world.getBlockAt(x, surfaceY, z);
                if (!feet.isEmpty() || !feet.getRelative(0, 1, 0).isEmpty() || unsafeGround(feet.getRelative(0, -1, 0).getType())) return false;
            }
        }
        for (int depth = 1; depth <= conf.requiredSolidDepth; depth++) {
            Block below = center.getRelative(0, -depth, 0);
            if (!below.getType().isSolid() || below.isLiquid()) return false;
        }
        return true;
    }

    private static boolean unsafeGround(Material material) {
        if (!material.isSolid()) return true;
        String name = material.name();
        return material == Material.BARRIER || material == Material.BEDROCK || material == Material.CACTUS
                || material == Material.MAGMA_BLOCK || material == Material.CAMPFIRE
                || material == Material.SOUL_CAMPFIRE || material == Material.POINTED_DRIPSTONE
                || name.contains("LEAVES") || name.contains("FENCE") || name.contains("WALL")
                || name.contains("SLAB") || name.contains("STAIRS");
    }

    private int randomBetween(int min, int max) { return min >= max ? min : min + random.nextInt(max - min + 1); }
    private static boolean same(Location first, Location second) {
        return first.getWorld() != null && first.getWorld().equals(second.getWorld())
                && first.getBlockX() == second.getBlockX() && first.getBlockY() == second.getBlockY() && first.getBlockZ() == second.getBlockZ();
    }
    private void broadcastAnnouncement(List<String> lines, Sound sound, float volume, float pitch, String winner) {
        SoundBuilder audio = new SoundBuilder(sound, volume, pitch);
        for (Player player : Bukkit.getOnlinePlayers()) sendAnnouncement(player, lines, audio, winner);
    }

    private void sendAnnouncement(Player player, List<String> lines, SoundBuilder sound, String winner) {
        if (lines == null || lines.isEmpty() || location == null) return;
        List<String> rendered = lines.stream().map(line -> line
                .replace("<x>", String.valueOf(location.getBlockX()))
                .replace("<y>", String.valueOf(location.getBlockY()))
                .replace("<z>", String.valueOf(location.getBlockZ()))
                .replace("<hits>", String.valueOf(hitsRemaining))
                .replace("<player>", winner == null ? "Unknown" : winner)).toList();
        Feedback.send(player, new ActionFeedback(rendered, null, sound));
    }
}
