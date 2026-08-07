package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import hmcore.HM_CORE;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class SilentAura extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTarget = settings.createGroup("Target");
    private final SettingGroup sgFilters = settings.createGroup("Filters");
    private final SettingGroup sgWeapon = settings.createGroup("Weapon");
    private final SettingGroup sgVisual = settings.createGroup("Visual");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
            .name("range")
            .description("Дальность атаки")
            .defaultValue(4.5)
            .min(1.0)
            .max(8.0)
            .sliderMax(8.0)
            .build()
    );

    public final Setting<Integer> attackDelay = sgGeneral.add(new IntSetting.Builder()
            .name("attack-delay")
            .description("Задержка между атаками (мс)")
            .defaultValue(100)
            .min(0)
            .max(500)
            .sliderMax(500)
            .build()
    );

    public final Setting<Integer> hitChance = sgGeneral.add(new IntSetting.Builder()
            .name("hit-chance")
            .description("Шанс попадания (в процентах)")
            .defaultValue(100)
            .min(1)
            .max(100)
            .sliderMax(100)
            .build()
    );

    public final Setting<Boolean> autoSwitchWeapon = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-switch-weapon")
            .description("Автоматически переключаться на лучшее оружие")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> onlyWhenHoldingWeapon = sgGeneral.add(new BoolSetting.Builder()
            .name("only-when-holding-weapon")
            .description("Атаковать только с оружием в руке")
            .defaultValue(false)
            .build()
    );

    // --- НАСТРОЙКИ ЦЕЛИ ---

    public final Setting<SortPriority> targetPriority = sgTarget.add(new EnumSetting.Builder<SortPriority>()
            .name("target-priority")
            .description("Приоритет выбора цели")
            .defaultValue(SortPriority.LowestHealth)
            .build()
    );

    public final Setting<TargetMode> targetMode = sgTarget.add(new EnumSetting.Builder<TargetMode>()
            .name("target-mode")
            .description("Режим выбора цели")
            .defaultValue(TargetMode.Closest)
            .build()
    );

    public final Setting<Boolean> throughWalls = sgTarget.add(new BoolSetting.Builder()
            .name("through-walls")
            .description("Атака сквозь стены")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> ignoreInvisible = sgTarget.add(new BoolSetting.Builder()
            .name("ignore-invisible")
            .description("Игнорировать невидимых игроков")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> ignoreDead = sgTarget.add(new BoolSetting.Builder()
            .name("ignore-dead")
            .description("Игнорировать мёртвых")
            .defaultValue(true)
            .build()
    );

    // --- ФИЛЬТРЫ ---

    public final Setting<TargetFilter> targetFilter = sgFilters.add(new EnumSetting.Builder<TargetFilter>()
            .name("target-filter")
            .description("Какие существа атаковать")
            .defaultValue(TargetFilter.Players)
            .build()
    );

    public final Setting<Boolean> ignoreFriends = sgFilters.add(new BoolSetting.Builder()
            .name("ignore-friends")
            .description("Игнорировать друзей")
            .defaultValue(false)
            .build()
    );

    public final Setting<List<String>> friendsList = sgFilters.add(new StringListSetting.Builder()
            .name("friends-list")
            .description("Список друзей (через запятую)")
            .defaultValue()
            .build()
    );

    public final Setting<Boolean> ignoreTeams = sgFilters.add(new BoolSetting.Builder()
            .name("ignore-teams")
            .description("Игнорировать союзников по команде")
            .defaultValue(false)
            .build()
    );

    // --- НАСТРОЙКИ ОРУЖИЯ ---

    public final Setting<Boolean> autoCrit = sgWeapon.add(new BoolSetting.Builder()
            .name("auto-crit")
            .description("Автоматически наносить критические удары")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> onlySwords = sgWeapon.add(new BoolSetting.Builder()
            .name("only-swords")
            .description("Использовать только мечи")
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

    public final Setting<Boolean> notifyKill = sgVisual.add(new BoolSetting.Builder()
            .name("notify-kill")
            .description("Уведомлять об убийстве")
            .defaultValue(false)
            .build()
    );

    // --- ПРОДВИНУТЫЕ ---

    public final Setting<Boolean> multiTarget = sgAdvanced.add(new BoolSetting.Builder()
            .name("multi-target")
            .description("Атаковать несколько целей одновременно")
            .defaultValue(false)
            .build()
    );

    public final Setting<Integer> maxTargets = sgAdvanced.add(new IntSetting.Builder()
            .name("max-targets")
            .description("Максимум целей для мульти-атаки")
            .defaultValue(2)
            .min(1)
            .max(5)
            .sliderMax(5)
            .build()
    );

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

    // --- ENUM'Ы ---

    public enum TargetMode {
        Closest("Ближайший"),
        Farthest("Самый дальний"),
        LowestHp("Самый слабый"),
        HighestHp("Самый сильный"),
        Random("Случайный");

        private final String name;

        TargetMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum TargetFilter {
        Players("Игроки"),
        Mobs("Мобы"),
        Passive("Пассивные"),
        Hostile("Враждебные"),
        All("Все");

        private final String name;

        TargetFilter(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // --- ПЕРЕМЕННЫЕ ---

    private long lastAttack = 0;
    private final Random random = new Random();
    private LivingEntity currentTarget = null;

    // --- КОНСТРУКТОР ---

    public SilentAura() {
        super(HM_CORE.CATEGORY, "SilentAura", "Скрытая KillAura без поворота головы с кучей настроек");
    }

    @Override
    public void onActivate() {
        lastAttack = 0;
        currentTarget = null;
    }

    @Override
    public void onDeactivate() {
        currentTarget = null;
    }

    // --- ТИК ---

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Проверка задержки
        long delay = attackDelay.get();
        if (randomDelay.get()) {
            delay += random.nextInt(randomDelayRange.get() + 1);
        }
        if (System.currentTimeMillis() - lastAttack < delay) return;

        // Проверка оружия
        if (onlyWhenHoldingWeapon.get() && !isHoldingWeapon()) return;

        // Поиск целей
        List<LivingEntity> targets = findTargets();
        if (targets.isEmpty()) {
            currentTarget = null;
            return;
        }

        // Обработка мульти-целей
        if (multiTarget.get()) {
            int count = Math.min(maxTargets.get(), targets.size());
            for (int i = 0; i < count; i++) {
                LivingEntity target = targets.get(i);
                if (shouldAttack(target)) {
                    attackEntity(target);
                }
            }
        } else {
            // Одна цель
            LivingEntity target = targets.get(0);
            if (shouldAttack(target)) {
                attackEntity(target);
                currentTarget = target;
            }
        }

        lastAttack = System.currentTimeMillis();
    }

    // --- ПОИСК ЦЕЛЕЙ ---

    private List<LivingEntity> findTargets() {
        List<LivingEntity> candidates = new ArrayList<>();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living == mc.player) continue;
            if (ignoreDead.get() && (living.isDead() || living.getHealth() <= 0)) continue;
            if (ignoreInvisible.get() && living.isInvisible()) continue;
            if (mc.player.distanceTo(living) > range.get()) continue;
            if (!throughWalls.get() && !mc.player.canSee(living)) continue;
            if (!isTargetTypeValid(living)) continue;
            if (ignoreFriends.get() && living instanceof PlayerEntity) {
                String name = ((PlayerEntity) living).getName().getString();
                for (String friend : friendsList.get()) {
                    if (name.equalsIgnoreCase(friend.trim())) {
                        continue;
                    }
                }
            }
            candidates.add(living);
        }

        if (candidates.isEmpty()) return candidates;

        // Сортировка по режиму
        switch (targetMode.get()) {
            case Closest -> candidates.sort(Comparator.comparingDouble(e -> mc.player.distanceTo(e)));
            case Farthest -> candidates.sort((a, b) -> Double.compare(mc.player.distanceTo(b), mc.player.distanceTo(a)));
            case LowestHp -> candidates.sort(Comparator.comparingDouble(LivingEntity::getHealth));
            case HighestHp -> candidates.sort((a, b) -> Double.compare(b.getHealth(), a.getHealth()));
            case Random -> {
                LivingEntity randomTarget = candidates.get(random.nextInt(candidates.size()));
                candidates.clear();
                candidates.add(randomTarget);
            }
        }

        return candidates;
    }

    // --- ПРОВЕРКА ТИПА ЦЕЛИ ---

    private boolean isTargetTypeValid(LivingEntity target) {
        switch (targetFilter.get()) {
            case Players -> {
                return target instanceof PlayerEntity;
            }
            case Mobs -> {
                return target instanceof MobEntity;
            }
            case Passive -> {
                return target instanceof PassiveEntity;
            }
            case Hostile -> {
                return target instanceof MobEntity && ((MobEntity) target).isHostile();
            }
            case All -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    // --- ПРОВЕРКА НА АТАКУ ---

    private boolean shouldAttack(LivingEntity target) {
        if (target == null) return false;
        if (hitChance.get() < 100 && random.nextInt(100) > hitChance.get()) return false;
        return true;
    }

    // --- АТАКА ---

    private void attackEntity(LivingEntity target) {
        if (mc.player == null || mc.world == null) return;

        // Автопереключение оружия
        if (autoSwitchWeapon.get()) {
            int slot = findBestWeapon();
            if (slot != -1 && slot != mc.player.getInventory().selectedSlot) {
                mc.player.getInventory().selectedSlot = slot;
            }
        }

        // Крит
        if (autoCrit.get() && mc.player.isOnGround()) {
            mc.player.jump();
        }

        // Атака
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        // Уведомление об убийстве
        if (notifyKill.get() && target.getHealth() <= 0) {
            String name = target instanceof PlayerEntity ? ((PlayerEntity) target).getName().getString() : target.getType().getName().getString();
            mc.player.sendMessage(Text.literal("§c⚔ §6Убийство: §f" + name), false);
        }
    }

    // --- ПРОВЕРКА ОРУЖИЯ В РУКЕ ---

    private boolean isHoldingWeapon() {
        if (mc.player == null) return false;
        ItemStack stack = mc.player.getMainHandStack();
        if (onlySwords.get()) {
            return stack.getItem() instanceof SwordItem;
        }
        return stack.getItem() instanceof SwordItem || stack.getItem() == Items.AXE;
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
                damage = sword.getAttackDamage() + (double)sword.getAttackSpeed();
            } else if (stack.getItem() == Items.AXE) {
                damage = 6.0; // Упрощённо
            }
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
            return "§c" + name;
        }
        return "§7Ожидание...";
    }
}
