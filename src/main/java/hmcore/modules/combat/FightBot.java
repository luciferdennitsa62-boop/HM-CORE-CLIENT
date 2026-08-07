package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import hmcore.HM_CORE;

import java.util.Random;

public class FightBot extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgCombat = settings.createGroup("Combat");
    private final SettingGroup sgHealth = settings.createGroup("Health");
    private final SettingGroup sgMovement = settings.createGroup("Movement");
    private final SettingGroup sgVisual = settings.createGroup("Visual");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
            .name("range")
            .description("Дальность поиска цели")
            .defaultValue(5.0)
            .min(1.0)
            .max(10.0)
            .sliderMax(10.0)
            .build()
    );

    public final Setting<Integer> attackDelay = sgGeneral.add(new IntSetting.Builder()
            .name("attack-delay")
            .description("Задержка между атаками (мс)")
            .defaultValue(200)
            .min(50)
            .max(1000)
            .sliderMax(1000)
            .build()
    );

    public final Setting<Boolean> autoSwitchWeapon = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-switch-weapon")
            .description("Автоматически переключаться на лучшее оружие")
            .defaultValue(true)
            .build()
    );

    // --- НАСТРОЙКИ БОЯ ---

    public final Setting<CombatMode> combatMode = sgCombat.add(new EnumSetting.Builder<CombatMode>()
            .name("combat-mode")
            .description("Режим боя")
            .defaultValue(CombatMode.Aggressive)
            .build()
    );

    public final Setting<Boolean> crits = sgCombat.add(new BoolSetting.Builder()
            .name("crits")
            .description("Наносить критические удары")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> strafe = sgCombat.add(new BoolSetting.Builder()
            .name("strafe")
            .description("Двигаться вокруг цели (зигзаг)")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> onlySwords = sgCombat.add(new BoolSetting.Builder()
            .name("only-swords")
            .description("Использовать только мечи")
            .defaultValue(false)
            .build()
    );

    // --- ЗДОРОВЬЕ ---

    public final Setting<Integer> eatHealth = sgHealth.add(new IntSetting.Builder()
            .name("eat-health")
            .description("При каком здоровье есть еду")
            .defaultValue(8)
            .min(1)
            .max(19)
            .sliderMax(19)
            .build()
    );

    public final Setting<Integer> potionHealth = sgHealth.add(new IntSetting.Builder()
            .name("potion-health")
            .description("При каком здоровье пить зелье")
            .defaultValue(6)
            .min(1)
            .max(19)
            .sliderMax(19)
            .build()
    );

    public final Setting<Boolean> useGapples = sgHealth.add(new BoolSetting.Builder()
            .name("use-gapples")
            .description("Использовать золотые яблоки")
            .defaultValue(true)
            .build()
    );

    // --- ДВИЖЕНИЕ ---

    public final Setting<Boolean> chaseTarget = sgMovement.add(new BoolSetting.Builder()
            .name("chase-target")
            .description("Преследовать цель")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> jumpAround = sgMovement.add(new BoolSetting.Builder()
            .name("jump-around")
            .description("Прыгать во время боя")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> dodge = sgMovement.add(new BoolSetting.Builder()
            .name("dodge")
            .description("Уклоняться от атак")
            .defaultValue(false)
            .build()
    );

    // --- ВИЗУАЛ ---

    public final Setting<Boolean> showTarget = sgVisual.add(new BoolSetting.Builder()
            .name("show-target")
            .description("Показывать текущую цель")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> notifyStatus = sgVisual.add(new BoolSetting.Builder()
            .name("notify-status")
            .description("Уведомлять о состоянии боя")
            .defaultValue(false)
            .build()
    );

    // --- ПРОДВИНУТЫЕ ---

    public final Setting<Boolean> randomDelay = sgAdvanced.add(new BoolSetting.Builder()
            .name("random-delay")
            .description("Случайная задержка (сложнее детектить)")
            .defaultValue(false)
            .build()
    );

    public final Setting<Integer> randomDelayRange = sgAdvanced.add(new IntSetting.Builder()
            .name("random-delay-range")
            .description("Диапазон случайной задержки (мс)")
            .defaultValue(50)
            .min(0)
            .max(200)
            .sliderMax(200)
            .build()
    );

    // --- ENUM ---
    public enum CombatMode {
        Aggressive("Агрессивный"),
        Defensive("Защитный"),
        Balanced("Сбалансированный");

        private final String name;

        CombatMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // --- ПЕРЕМЕННЫЕ ---
    private long lastAttack = 0;
    private long lastHeal = 0;
    private final Random random = new Random();
    private LivingEntity currentTarget = null;
    private Vec3d strafeDirection = null;

    // --- КОНСТРУКТОР ---
    public FightBot() {
        super(HM_CORE.CATEGORY, "FightBot", "Полностью автоматический бой");
    }

    @Override
    public void onActivate() {
        lastAttack = 0;
        lastHeal = 0;
        currentTarget = null;
        strafeDirection = null;
        if (notifyStatus.get()) {
            info("§aFightBot активирован! Режим: " + combatMode.get());
        }
    }

    @Override
    public void onDeactivate() {
        currentTarget = null;
        if (notifyStatus.get()) {
            info("§cFightBot деактивирован");
        }
    }

    // --- ТИК ---
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // --- ЛЕЧЕНИЕ ---
        handleHealing();

        // --- ПОИСК ЦЕЛИ ---
        currentTarget = (LivingEntity) TargetUtils.getPlayerTarget(range.get(), SortPriority.LowestHealth);
        if (currentTarget == null) {
            // Если нет цели, но бот агрессивный — ищем мобов
            if (combatMode.get() == CombatMode.Aggressive) {
                // Мобы добавлять позже, пока просто возвращаемся
            }
            return;
        }

        // --- ДВИЖЕНИЕ ---
        handleMovement();

        // --- АТАКА ---
        handleAttack();
    }

    // --- ЛЕЧЕНИЕ ---
    private void handleHealing() {
        if (mc.player == null) return;
        if (System.currentTimeMillis() - lastHeal < 2000) return;

        float health = mc.player.getHealth();

        // Золотые яблоки
        if (useGapples.get() && health < eatHealth.get()) {
            if (InvUtils.findInHotbar(Items.ENCHANTED_GOLDEN_APPLE).found()) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                lastHeal = System.currentTimeMillis();
                if (notifyStatus.get()) {
                    info("§aСъел золотое яблоко");
                }
                return;
            }
            if (InvUtils.findInHotbar(Items.GOLDEN_APPLE).found()) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                lastHeal = System.currentTimeMillis();
                if (notifyStatus.get()) {
                    info("§aСъел золотое яблоко");
                }
                return;
            }
        }

        // Обычная еда
        if (health < eatHealth.get()) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.getItem().isFood() && !stack.getItem().equals(Items.GOLDEN_APPLE) && !stack.getItem().equals(Items.ENCHANTED_GOLDEN_APPLE)) {
                    mc.player.getInventory().selectedSlot = i;
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    lastHeal = System.currentTimeMillis();
                    if (notifyStatus.get()) {
                        info("§aСъел еду");
                    }
                    return;
                }
            }
        }

        // Зелья лечения (упрощённо)
        if (health < potionHealth.get()) {
            // Поиск зелья лечения в инвентаре
            // Это сложно, упростим
            if (mc.player.hasStatusEffect(StatusEffects.REGENERATION)) {
                // Уже есть реген
            }
        }
    }

    // --- ДВИЖЕНИЕ ---
    private void handleMovement() {
        if (mc.player == null || currentTarget == null) return;

        if (chaseTarget.get() && mc.player.distanceTo(currentTarget) > range.get() / 2) {
            // Преследование
            Vec3d direction = currentTarget.getPos().subtract(mc.player.getPos()).normalize();
            mc.player.setVelocity(direction.x * 0.5, mc.player.getVelocity().y, direction.z * 0.5);
        }

        if (strafe.get() && mc.player.distanceTo(currentTarget) < range.get()) {
            // Движение вокруг цели
            if (strafeDirection == null || random.nextDouble() < 0.1) {
                strafeDirection = new Vec3d(
                        (random.nextDouble() - 0.5) * 2,
                        0,
                        (random.nextDouble() - 0.5) * 2
                ).normalize();
            }
            mc.player.setVelocity(strafeDirection.x * 0.3, mc.player.getVelocity().y, strafeDirection.z * 0.3);
        }

        if (jumpAround.get() && random.nextDouble() < 0.05) {
            mc.player.jump();
        }

        if (dodge.get() && random.nextDouble() < 0.05) {
            // Уклонение — просто прыжок в сторону
            mc.player.jump();
        }
    }

    // --- АТАКА ---
    private void handleAttack() {
        if (mc.player == null || currentTarget == null) return;

        // Проверка задержки
        long delay = attackDelay.get();
        if (randomDelay.get()) {
            delay += random.nextInt(randomDelayRange.get() + 1);
        }
        if (System.currentTimeMillis() - lastAttack < delay) return;

        // Проверка дистанции
        if (mc.player.distanceTo(currentTarget) > range.get()) return;

        // Автопереключение оружия
        if (autoSwitchWeapon.get()) {
            int bestSlot = findBestWeapon();
            if (bestSlot != -1 && bestSlot != mc.player.getInventory().selectedSlot) {
                mc.player.getInventory().selectedSlot = bestSlot;
            }
        }

        // Криты
        if (crits.get() && mc.player.isOnGround()) {
            mc.player.jump();
        }

        // Атака
        mc.interactionManager.attackEntity(mc.player, currentTarget);
        mc.player.swingHand(Hand.MAIN_HAND);
        lastAttack = System.currentTimeMillis();

        if (notifyStatus.get() && random.nextDouble() < 0.1) {
            info("§cАтака на " + currentTarget.getName().getString() + " (HP: " + String.format("%.1f", currentTarget.getHealth()) + ")");
        }
    }

    // --- ПОИСК ЛУЧШЕГО ОРУЖИЯ ---
    private int findBestWeapon() {
        if (mc.player == null) return -1;
        int bestSlot = -1;
        double bestDamage = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            double damage = 0;
            if (stack.getItem() instanceof net.minecraft.item.SwordItem sword) {
                damage = sword.getAttackDamage();
            } else if (stack.getItem() == Items.AXE) {
                damage = 6.0;
            }
            if (onlySwords.get() && !(stack.getItem() instanceof net.minecraft.item.SwordItem)) continue;
            if (damage > bestDamage) {
                bestDamage = damage;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    // --- ИНФОРМАЦИЯ В HUD ---
    @Override
    public String getInfoString() {
        if (currentTarget != null && showTarget.get()) {
            String name = currentTarget instanceof PlayerEntity ? ((PlayerEntity) currentTarget).getName().getString() : currentTarget.getType().getName().getString();
            return "§c" + name + " §7" + String.format("%.1f", currentTarget.getHealth());
        }
        return "§7Поиск...";
    }
}
