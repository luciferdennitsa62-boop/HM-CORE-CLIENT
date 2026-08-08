package hmcore;

import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.Category;

// --- COMBAT МОДУЛИ ---
import hmcore.modules.combat.MaceDMG;
import hmcore.modules.combat.AutoCrit;
import hmcore.modules.combat.AutoCrystal;
import hmcore.modules.combat.Surround;
import hmcore.modules.combat.SelfTrap;
import hmcore.modules.combat.Burrow;
import hmcore.modules.combat.AutoTrap;
import hmcore.modules.combat.SilentAura;
import hmcore.modules.combat.FightBot;
import hmcore.modules.combat.AnchorAura;
import hmcore.modules.combat.SmartFly;
import hmcore.modules.combat.PacketFly;
import hmcore.modules.combat.BlinkTP;
import hmcore.modules.combat.ServerSideClickTP;
import hmcore.modules.combat.Reach;
import hmcore.modules.combat.DamageStack;
import hmcore.modules.combat.WeaponManager;
import hmcore.modules.combat.NoFallPlus;
import hmcore.modules.combat.Disabler;
import hmcore.modules.combat.GodMode;
import hmcore.modules.combat.MegaJump;
import hmcore.modules.combat.AntiVoid;
import hmcore.modules.combat.AntiPotion;
import hmcore.modules.combat.AntiBookBan;
import hmcore.modules.combat.NameProtect;
import hmcore.modules.combat.PingSpoof;
import hmcore.modules.combat.EntitySpeed;
import hmcore.modules.combat.NoRender;
import hmcore.modules.combat.Animations;
import hmcore.modules.combat.MacroHub;
import hmcore.modules.combat.BaseFinder;
import hmcore.modules.combat.OPCrack;
import hmcore.modules.combat.AdaptiveBypass;

// --- VISUALS МОДУЛИ ---
import hmcore.modules.visuals.TrailEffect;
import hmcore.modules.visuals.RedBlackTheme;
import hmcore.modules.visuals.LSDMode;
import hmcore.modules.visuals.Chams;

// --- КОМАНДЫ ---
import hmcore.commands.ToggleCommand;

public class HM_CORE extends MeteorAddon {

    public static final Category CATEGORY = new Category("HM-CORE");

    @Override
    public void onInitialize() {
        info("HM-CORE-CLIENT загружается...");

        // ============================================================
        //  COMBAT (БОЕВЫЕ МОДУЛИ)
        // ============================================================

        Modules.get().add(new MaceDMG());
        Modules.get().add(new AutoCrit());
        Modules.get().add(new AutoCrystal());
        Modules.get().add(new Surround());
        Modules.get().add(new SelfTrap());
        Modules.get().add(new Burrow());
        Modules.get().add(new AutoTrap());
        Modules.get().add(new SilentAura());
        Modules.get().add(new FightBot());
        Modules.get().add(new AnchorAura());
        Modules.get().add(new SmartFly());
        Modules.get().add(new PacketFly());
        Modules.get().add(new BlinkTP());
        Modules.get().add(new ServerSideClickTP());
        Modules.get().add(new Reach());
        Modules.get().add(new DamageStack());
        Modules.get().add(new WeaponManager());
        Modules.get().add(new NoFallPlus());
        Modules.get().add(new Disabler());
        Modules.get().add(new GodMode());
        Modules.get().add(new MegaJump());
        Modules.get().add(new AntiVoid());
        Modules.get().add(new AntiPotion());
        Modules.get().add(new AntiBookBan());
        Modules.get().add(new NameProtect());
        Modules.get().add(new PingSpoof());
        Modules.get().add(new EntitySpeed());
        Modules.get().add(new NoRender());
        Modules.get().add(new Animations());
        Modules.get().add(new MacroHub());
        Modules.get().add(new BaseFinder());
        Modules.get().add(new OPCrack());
        Modules.get().add(new AdaptiveBypass());

        // ============================================================
        //  VISUALS (ВИЗУАЛЬНЫЕ МОДУЛИ)
        // ============================================================

        Modules.get().add(new TrailEffect());
        Modules.get().add(new RedBlackTheme());
        Modules.get().add(new LSDMode());
        Modules.get().add(new Chams());

        // ============================================================
        //  КОМАНДЫ
        // ============================================================

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
