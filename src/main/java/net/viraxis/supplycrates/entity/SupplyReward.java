package net.viraxis.supplycrates.entity;

import com.massivecraft.massivecore.util.ItemBuilder;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public class SupplyReward {
    public double weight = 1D;
    public int minimumAmount = 1;
    public int maximumAmount = 1;
    public ItemBuilder item = new ItemBuilder(Material.DIAMOND)
            .withDisplayName("&b<#66D9E8><bold>Supply Crate Reward");
    public List<String> commands = new ArrayList<>();

    public SupplyReward() {}
    public SupplyReward(double weight, int min, int max, ItemBuilder item) {
        this.weight = weight; this.minimumAmount = min; this.maximumAmount = max; this.item = item;
    }
}
