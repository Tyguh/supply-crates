package net.viraxis.supplycrates.entity;

import com.massivecraft.massivecore.store.Coll;
import com.massivecraft.massivecore.store.MConf;
import net.viraxis.supplycrates.SupplyCratesPlugin;

public final class SupplyCratesConfColl extends Coll<SupplyCratesConf> {
    private static final SupplyCratesConfColl i = new SupplyCratesConfColl();
    public static SupplyCratesConfColl get() { return i; }
    private SupplyCratesConfColl() { super("supplycrates_conf", SupplyCratesConf.class, MConf.getDb("supplycrates_conf"), SupplyCratesPlugin.get()); }
    @Override public void setActive(boolean active) {
        super.setActive(active);
        if (active) SupplyCratesConf.instance = get("conf", true);
    }
}
