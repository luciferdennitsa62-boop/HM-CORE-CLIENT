package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import hmcore.HM_CORE;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class DamageStack extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTarget = settings.createGroup("Target");
    private final SettingGroup sgBypass = settings.createGroup("Bypass");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");
    private final SettingGroup sgVisual = settings.createGroup("Visual");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Режим нанесения урона")
            .defaultValue(Mode.Packet)
            .build()
    );

    public final Setting<Integer> attacksPerTick = sgGeneral.add(new IntSetting.Builder()
            .name("attacks-per-tick")
            .description("Количество атак за один тик")
            .defaultValue(3)
            .min(1)
            .max(10)
            .sliderMax(10)
            .build()
    );

    public final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
            .name("delay")
            .description("Задержка между применениями (мс)")
            .defaultValue(100)
            .min(0)
            .max(500)
            .sliderMax(500)
            .build()
    );

    public final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
            .name("range")
            .description("Дальность атаки")
            .defaultValue(4.5)
            .min(1.0)
            .max(8.0)
            .sliderMax(8.0)
            .build()
    );

    // --- НАСТРОЙКИ ЦЕЛИ ---

    public final Setting<Boolean> playersOnly = sgTarget.add(new BoolSetting.Builder()
            .name("players-only")
            .description("Атаковать только игроков")
            .defaultValue(true)
            .build()
    );

    public final Setting<SortPriority> targetPriority = sgTarget.add(new EnumSetting.Builder<SortPriority>()
            .name("target-priority")
            .description("Приоритет цели")
            .defaultValue(SortPriority.LowestHealth)
            .build()
    );

    public final Setting<Boolean> ignoreFriends = sgTarget.add(new BoolSetting.Builder()
            .name("ignore-friends")
            .description("Игнорировать друзей")
            .defaultValue(false)
            .build()
    );

    public final Setting<List<String>> friendsList = sgTarget.add(new StringListSetting.Builder()
            .name("friends-list")
            .description("Список друзей (через запятую)")
            .defaultValue()
            .build()
    );

    // --- НАСТРОЙКИ ОБХОДА ---

    public final Setting<BypassType> bypass = sgBypass.add(new EnumSetting.Builder<BypassType>()
            .name("bypass")
            .description("Метод обхода античита")
            .defaultValue(BypassType.None)
            .build()
    );

    public final Setting<Double> spoofY = sgBypass.add(new DoubleSetting.Builder()
            .name("spoof-y")
            .description("Смещение по Y для обмана")
            .defaultValue(0.01)
            .min(0.001)
            .max(0.1)
            .sliderMax(0.1)
            .build()
    );

    public final Setting<Boolean> randomSpoof = sgBypass.add(new BoolSetting.Builder()
            .name("random-spoof")
            .description("Случайное смещение")
            .defaultValue(true)
            .build()
    );

    // --- ПРОДВИНУТЫЕ ---

    public final Setting<Boolean> autoSwitchWeapon = sgAdvanced.add(new BoolSetting.Builder()
            .name("auto-switch-weapon")
            .description("Автоматически переключаться на лучшее оружие")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> requireWeapon = sgAdvanced.add(new BoolSetting.Builder()
            .name("require-weapon")
            .description("Требовать оружие в руке")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> onlyOnGround = sgAdvanced.add(new BoolSetting.Builder()
            .name("only-on-ground")
            .description("Только на земле")
            .defaultValue(false)
            .build()
    );

    // --- ВИЗУАЛ ---

    public final Setting<Boolean> notify = sgVisual.add(new BoolSetting.Builder()
            .name("notify")
            .description("Уведомлять о применении")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> showDamage = sgVisual.add(new BoolSetting.Builder()
            .name("show-damage")
            .description("Показывать расчётный урон")
            .defaultValue(true)
            .build()
    );

    // --- ENUM'Ы ---
    public enum Mode {
        Packet("Пакетный"),
        Combat("Боевой"),
        Hybrid("Гибридный"),
        Grim("GrimAC");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum BypassType {
        None("Выкл"),
        Spoof("Подделка позиции"),
        PacketSpoof("Подделка пакетов"),
        Full("Полный обход");

        private final String name;

        BypassType(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // --- ПЕРЕМЕННЫЕ ---
    private long lastUseTime = 0;
    private final Random random = new Random();

    // --- КОНСТРУКТОР ---
    public DamageStack() {
        super(HM_CORE.CATEGORY, "DamageStack", "Мгновенный урон (несколько атак за тик)");
    }

    @Override
    public void onActivate() {
        lastUseTime = 0;
    }

    // --- ТИК ---
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (System.currentTimeMillis() - lastUseTime < delay.get()) return;

        // Проверка на земле
        if (onlyOnGround.get() && !mc.player.isOnGround()) return;

        // Поиск цели
        LivingEntity target = (LivingEntity) TargetUtils.getPlayerTarget(range.get(), targetPriority.get());
        if (target == null) return;

        // Проверка типа цели
        if (playersOnly.get() && !(target instanceof PlayerEntity)) return;

        // Проверка друзей
        if (ignoreFriends.get() && target instanceof PlayerEntity) {
            String name = ((PlayerEntity) target).getName().getString();
            for (String friend : friendsList.get()) {
                if (name.equalsIgnoreCase(friend.trim())) {
                    return;
                }
            }
        }

        // Проверка оружия
        if (requireWeapon.get() && !isHoldingWeapon()) return;

        // Проверка дистанции
        if (mc.player.distanceTo(target) > range.get()) return;

        // --- НАНЕСЕНИЕ УРОНА ---
        for (int i = 0; i < attacksPerTick.get(); i++) {
            switch (mode.get()) {
                case Packet -> attackPacket(target);
                case Combat -> attackCombat(target);
                case Hybrid -> attackHybrid(target);
                case Grim -> attackGrim(target);
            }

            // Небольшая задержка между атаками в рамках одного тика
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        lastUseTime = System.currentTimeMillis();

        if (notify.get()) {
            info("§c" + attacksPerTick.get() + "x атак на " + target.getName().getString());
        }
    }

    // --- ПАКЕТНЫЙ РЕЖИМ ---
    private void attackPacket(LivingEntity target) {
        if (mc.player == null) return;

        // Обход античита: отправка фейковых пакетов
        if (bypass.get() != BypassType.None) {
            double offset = randomSpoof.get() ? spoofY.get() * (random.nextDouble() + 0.5) : spoofY.get();
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), mc.player.getY() + offset, mc.player.getZ(), false
            ));
        }

        // Атака
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        // Сброс позиции (если был обход)
        if (bypass.get() != BypassType.None) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
        }
    }

    // --- БОЕВОЙ РЕЖИМ ---
    private void attackCombat(LivingEntity target) {
        if (mc.player == null) return;

        // Автопереключение оружия
        if (autoSwitchWeapon.get()) {
            int bestSlot = findBestWeapon();
            if (bestSlot != -1 && bestSlot != mc.player.getInventory().selectedSlot) {
                mc.player.getInventory().selectedSlot = bestSlot;
            }
        }

        // Атака
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        // Быстрый повтор через пакеты (для повышения урона)
        if (bypass.get() == BypassType.PacketSpoof) {
            for (int i = 0; i < 2; i++) {
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
            }
        }
    }

    // --- ГИБРИДНЫЙ РЕЖИМ ---
    private void attackHybrid(LivingEntity target) {
        if (mc.player == null) return;

        // Автопереключение
        if (autoSwitchWeapon.get()) {
            int bestSlot = findBestWeapon();
            if (bestSlot != -1 && bestSlot != mc.player.getInventory().selectedSlot) {
                mc.player.getInventory().selectedSlot = bestSlot;
            }
        }

        // Пакетная атака
        for (int i = 0; i < 2; i++) {
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);

            // Подделка позиции между атаками
            if (bypass.get() == BypassType.Full) {
                double offset = randomSpoof.get() ? spoofY.get() * (random.nextDouble() + 0.3) : spoofY.get();
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        mc.player.getX(), mc.player.getY() + offset, mc.player.getZ(), false
                ));
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
            }
        }
    }

    // --- GRIM РЕЖИМ ---
    private void attackGrim(LivingEntity target) {
        if (mc.player == null) return;

        // Grim: специфические значения
        double y = mc.player.getY();

        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), y + 0.001091981, mc.player.getZ(), false
        ));

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), y + 0.000114514, mc.player.getZ(), true
        ));
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
            if (stack.getItem() instanceof SwordItem sword) {
                damage = sword.getAttackDamage();
            } else if (stack.getItem() == Items.AXE) {
                damage = 6.0;
            }
            if (damage > bestDamage) {
                bestDamage = damage;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    // --- ПРОВЕРКА ОРУЖИЯ В РУКЕ ---
    private boolean isHoldingWeapon() {
        if (mc.player == null) return false;
        ItemStack stack = mc.player.getMainHandStack();
        return stack.getItem() instanceof SwordItem || stack.getItem() == Items.AXE;
    }

    // --- ИНФОРМАЦИЯ В HUD ---
    @Override
    public String getInfoString() {
        if (showDamage.get()) {
            return "§c" + attacksPerTick.get() + "x";
        }
        return null;
    }
}
