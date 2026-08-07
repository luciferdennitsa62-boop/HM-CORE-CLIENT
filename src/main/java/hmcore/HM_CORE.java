package hmcore;

import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.Category;

// ИМПОРТЫ ВСЕХ МОДУЛЕЙ
import hmcore.modules.combat.MaceDMG;
import hmcore.modules.combat.AutoCrit;
import hmcore.modules.combat.AutoCrystal;
import hmcore.modules.combat.Surround;
import hmcore.modules.combat.SmartFly;
import hmcore.modules.combat.ServerSideClickTP;
import hmcore.modules.visuals.TrailEffect;
import hmcore.modules.visuals.RedBlackTheme;
import hmcore.commands.ToggleCommand;

public class HM_CORE extends MeteorAddon {

    public static final Category CATEGORY = new Category("HM-CORE");

    @Override
    public void onInitialize() {
        info("HM-CORE-CLIENT загружается...");

        // --- COMBAT (БОЕВЫЕ) ---
        Modules.get().add(new MaceDMG());
        Modules.get().add(new AutoCrit());
        Modules.get().add(new AutoCrystal());
        Modules.get().add(new Surround());

        // --- MOVEMENT (ДВИЖЕНИЕ) - всё в combat по твоему желанию ---
        Modules.get().add(new SmartFly());
        Modules.get().add(new ServerSideClickTP());

        // --- VISUALS (ВИЗУАЛ) ---
        Modules.get().add(new TrailEffect());
        Modules.get().add(new RedBlackTheme());

        // --- КОМАНДЫ ---
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
