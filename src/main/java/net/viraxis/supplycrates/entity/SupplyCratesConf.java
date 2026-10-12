package net.viraxis.supplycrates.entity;

import com.massivecraft.massivecore.store.Entity;
import com.massivecraft.massivecore.util.ItemBuilder;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SupplyCratesConf extends Entity<SupplyCratesConf> {
    protected static transient SupplyCratesConf instance;
    public static SupplyCratesConf get() { return instance; }

    public boolean enabled = true;
    public String world = "world";
    public String region = "warzone";
    public Material crateMaterial = Material.CHEST;
    public boolean useFallingBlock = true;
    public Material fallingMaterial = Material.BARREL;
    public int fallingHeight = 15;
    public double fallingBlocksPerTick = 0.16D;
    public boolean dropRewardsAtCrate = true;
    public int requiredHits = 25;
    public int minimumPlayers = 1;
    public int minimumIntervalMinutes = 30;
    public int maximumIntervalMinutes = 60;
    public int lifetimeMinutes = 10;
    public int rewardRolls = 10;
    public int hitCooldownMillis = 175;
    public int signalSeconds = 15;
    public int signalIntervalSeconds = 15;
    public int locationAttempts = 250;
    public int safeSurfaceRadius = 2;
    public int requiredSolidDepth = 2;
    public int maximumNearbySlope = 1;
    public List<String> hologram = new ArrayList<>(List.of(
            "&6<#E69A30><bold>✧ &e<#F4D35E>SUPPLY CRATE &6<#E69A30>✧",
            "&f<#F5F5F5><hits> &7<#B0B0B0>hits remaining",
            "&7<#B0B0B0>Break it open to claim the rewards!"));
    public List<String> spawnAnnouncement = new ArrayList<>(List.of(
            "",
            "<center>&6<#E69A30><bold>✦ SUPPLY DROP INBOUND ✦</bold></center>",
            "<center>&f<#F5F5F5>A Supply Crate is descending into the &c<#E76A6A>Warzone&f<#F5F5F5>.</center>",
            "<center>&7<#B0B0B0>Follow the &b<#66D9E8>blue signal&7<#B0B0B0> and break it open for loot.</center>",
            "<center>&8<#666666>X: &f<#F5F5F5><x> &8<#666666>• Z: &f<#F5F5F5><z> &8<#666666>• &f<#F5F5F5><hits> hits</center>",
            ""));
    public List<String> joinReminder = new ArrayList<>(List.of(
            "",
            "<center>&6<#E69A30><bold>✦ UNCLAIMED SUPPLY DROP ✦</bold></center>",
            "<center>&7<#B0B0B0>A Supply Crate is still waiting in the &c<#E76A6A>Warzone&7<#B0B0B0>.</center>",
            "<center>&8<#666666>X: &f<#F5F5F5><x> &8<#666666>• Z: &f<#F5F5F5><z> &8<#666666>• &f<#F5F5F5><hits> hits remaining</center>",
            ""));
    public List<String> claimedAnnouncement = new ArrayList<>(List.of(
            "",
            "<center>&6<#E69A30><bold>✦ SUPPLY DROP CLAIMED ✦</bold></center>",
            "<center>&f<#F5F5F5><player> &7<#B0B0B0>broke open the Supply Crate and claimed its loot.</center>",
            ""));
    public List<String> expiredAnnouncement = new ArrayList<>(List.of(
            "",
            "<center>&c<#E76A6A><bold>✦ SUPPLY DROP LOST ✦</bold></center>",
            "<center>&7<#B0B0B0>The Supply Crate disappeared before anyone broke it open.</center>",
            ""));
    public List<SupplyReward> rewards = defaults();

    private static List<SupplyReward> defaults() {
        return new ArrayList<>(List.of(
                new SupplyReward(40, 8, 16, new ItemBuilder(Material.GOLDEN_APPLE).withDisplayName("&6<#E69A30>Golden Apples")),
                new SupplyReward(30, 8, 16, new ItemBuilder(Material.DIAMOND).withDisplayName("&b<#66D9E8>Diamonds")),
                new SupplyReward(20, 16, 32, new ItemBuilder(Material.EMERALD).withDisplayName("&a<#6FD96F>Emeralds")),
                new SupplyReward(10, 1, 2, new ItemBuilder(Material.NETHERITE_INGOT).withDisplayName("&8<#666666>Netherite Ingots"))));
    }

    @Override public SupplyCratesConf load(@NotNull SupplyCratesConf that) {
        super.load(that);
        requiredHits = Math.max(1, requiredHits);
        minimumPlayers = Math.max(0, minimumPlayers);
        minimumIntervalMinutes = Math.max(1, minimumIntervalMinutes);
        maximumIntervalMinutes = Math.max(minimumIntervalMinutes, maximumIntervalMinutes);
        lifetimeMinutes = Math.max(1, lifetimeMinutes);
        rewardRolls = Math.max(1, rewardRolls);
        fallingHeight = Math.max(1, fallingHeight);
        fallingBlocksPerTick = Math.clamp(fallingBlocksPerTick, 0.05D, 0.5D);
        hitCooldownMillis = Math.max(100, hitCooldownMillis);
        signalSeconds = Math.max(0, signalSeconds);
        signalIntervalSeconds = Math.max(1, signalIntervalSeconds);
        locationAttempts = Math.max(10, locationAttempts);
        safeSurfaceRadius = Math.clamp(safeSurfaceRadius, 1, 4);
        requiredSolidDepth = Math.clamp(requiredSolidDepth, 1, 5);
        maximumNearbySlope = Math.clamp(maximumNearbySlope, 0, 2);
        if (hologram == null) hologram = new ArrayList<>();
        if (spawnAnnouncement == null) spawnAnnouncement = new ArrayList<>();
        if (joinReminder == null) joinReminder = new ArrayList<>();
        if (claimedAnnouncement == null) claimedAnnouncement = new ArrayList<>();
        if (expiredAnnouncement == null) expiredAnnouncement = new ArrayList<>();
        if (rewards == null) rewards = defaults();
        return this;
    }
}
