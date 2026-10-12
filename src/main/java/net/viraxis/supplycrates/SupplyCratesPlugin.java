package net.viraxis.supplycrates;

import com.massivecraft.massivecore.MassivePlugin;
import net.viraxis.supplycrates.command.SupplyCrateCommand;
import net.viraxis.supplycrates.entity.SupplyCratesConfColl;
import net.viraxis.supplycrates.manager.SupplyCrateManager;

public final class SupplyCratesPlugin extends MassivePlugin {
    private static SupplyCratesPlugin instance;
    public static SupplyCratesPlugin get() { return instance; }

    @Override public void onEnableInner() {
        instance = this;
        activate(SupplyCratesConfColl.class, SupplyCrateManager.class);
        SupplyCrateCommand command = new SupplyCrateCommand();
        getCommand("supplycrate").setExecutor(command);
        getCommand("supplycrate").setTabCompleter(command);
    }

    @Override public void onDisable() {
        SupplyCrateManager.get().shutdown();
        super.onDisable();
    }

    @Override public boolean isVersionSynchronized() { return false; }
}
