package hmcore;

import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.Category;

// --- ВСЕ МОДУЛИ ---
import hmcore.modules.combat.*;
import hmcore.modules.visuals.*;
import hmcore.commands.ToggleCommand;

public class HM_CORE extends MeteorAddon {

    public static final Category CATEGORY = new Category("HM-CORE");

    @Override
    public void onInitialize() {
        info("HM-CORE-CLIENT загружается...");

        // --- COMBAT ---
        Modules.get().add(new MaceDMG());
        Modules.get().add(new AutoCrit());
        Modules.get().add(new AutoCrystal());
        Modules.get().add(new Surround());
        Modules.get().add(new SmartFly());
        Modules.get().add(new ServerSideClickTP());
        Modules.get().add(new SelfTrap());
        Modules.get().add(new Burrow());
        Modules.get().add(new PacketFly());
        Modules.get().add(new Reach());
        Modules.get().add(new GodMode());
        Modules.get().add(new Disabler());
        Modules.get().add(new NoFallPlus());
        Modules.get().add(new AnchorAura());
        Modules.get().add(new AutoTrap());

        // --- VISUALS ---
        Modules.get().add(new TrailEffect());
        Modules.get().add(new RedBlackTheme());
        Modules.get().add(new LSDMode());
        Modules.get().add(new Chams());

        // --- COMMANDS ---
        Commands.get().add(new ToggleCommand());

        info("HM-CORE-CLIENT успешно загружен! Все модули зарегистрированы.");
    }

    @Override
    public String getPackage() {
        return "hmcore";
    }

    @Override
    public String getName() {
        return "HM-CORE-CLIENT";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }
}
