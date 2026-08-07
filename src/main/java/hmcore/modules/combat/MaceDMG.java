package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import hmcore.HM_CORE;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class MaceDMG extends Module {

    // --- ГРУППЫ НАСТРОЕК ---
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTarget = settings.createGroup("Выбор цели");
    private final SettingGroup sgFilters = settings.createGroup("Фильтры");
    private final SettingGroup sgVisual = settings.createGroup("Визуал");
    private final SettingGroup sgAdvanced = settings.createGroup("Продвинутые");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Integer> fallHeight = sgGeneral.add(new IntSetting.Builder()
            .name("fall-height")
            .description("Высота падения для симуляции (1–1000). Чем выше, тем больше урон. ⚠️ Значения >30 могут вызвать бан на многих серверах!")
            .defaultValue(22)
            .min(1)
            .max(1000)
            .sliderMax(1000)
            .build()
    );

    public final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
            .name("cooldown")
            .description("Задержка между ударами (мс)")
            .defaultValue(100)
            .min(0)
            .max(2000)
            .sliderMax(1000)
            .build()
    );

    public final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-switch")
            .description("Автоматически переключаться на булаву")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> onlyOnGround = sgGeneral.add(new BoolSetting.Builder()
            .name("only-on-ground")
            .description("Работает только когда игрок на земле")
            .defaultValue(false)
            .build()
    );

    // --- НАСТРОЙКИ ВЫБОРА ЦЕЛИ ---

    public final Setting<TargetMode> targetMode = sgTarget.add(new EnumSetting.Builder<TargetMode>()
            .name("target-mode")
            .description("Режим выбора цели")
            .defaultValue(TargetMode.Nearest)
            .build()
    );

    public final Setting<Double> range = sgTarget.add(new DoubleSetting.Builder()
            .name("range")
            .description("Максимальная дистанция до цели")
            .defaultValue(4.5)
            .min(1.0)
            .max(10.0)
            .sliderMax(10.0)
            .build()
    );

    public final Setting<Boolean> throughWalls = sgTarget.add(new BoolSetting.Builder()
            .name("through-walls")
            .description("Искать цели сквозь стены")
            .defaultValue(false)
            .build()
    );

    // --- ФИЛЬТРЫ ЦЕЛЕЙ ---

    public final Setting<TargetFilter> targetFilter = sgFilters.add(new EnumSetting.Builder<TargetFilter>()
            .name("target-filter")
            .description("Какие существа атаковать")
            .defaultValue(TargetFilter.All)
            .build()
    );

    public final Setting<Boolean> ignoreInvisible = sgFilters.add(new BoolSetting.Builder()
            .name("ignore-invisible")
            .description("Игнорировать невидимых существ")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> ignoreDead = sgFilters.add(new BoolSetting.Builder()
            .name("ignore-dead")
            .description("Игнорировать мёртвых существ")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> ignoreFriends = sgFilters.add(new BoolSetting.Builder()
            .name("ignore-friends")
            .description("Игнорировать друзей (по списку имён)")
            .defaultValue(false)
            .build()
    );

    public final Setting<List<String>> friendsList = sgFilters.add(new StringListSetting.Builder()
            .name("friends-list")
            .description("Список имён друзей (через запятую)")
            .defaultValue()
            .build()
    );

    // --- ВИЗУАЛЬНЫЕ НАСТРОЙКИ ---

    public final Setting<Boolean> renderRange = sgVisual.add(new BoolSetting.Builder()
            .name("render-range")
            .description("Отображать радиус атаки")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> showDamage = sgVisual.add(new BoolSetting.Builder()
            .name("show-damage")
            .description("Показывать расчётный урон над целью")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> notify = sgVisual.add(new BoolSetting.Builder()
            .name("notify")
            .description("Уведомлять в чат о нанесённом уроне")
            .defaultValue(false)
            .build()
    );

    // --- ПРОДВИНУТЫЕ НАСТРОЙКИ ---

    public final Setting<Integer> packetCount = sgAdvanced.add(new IntSetting.Builder()
            .name("packet-count")
            .description("Количество пакетов для отправки (чем больше, тем надёжнее)")
            .defaultValue(5)
            .min(1)
            .max(20)
            .sliderMax(20)
            .build()
    );

    public final Setting<Boolean> useOffhand = sgAdvanced.add(new BoolSetting.Builder()
            .name("use-offhand")
            .description("Использовать булаву из оффхэнда")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> criticalMode = sgAdvanced.add(new BoolSetting.Builder()
            .name("critical-mode")
            .description("Добавлять дополнительный крит-пакет")
            .defaultValue(false)
            .build()
    );

    // --- ПЕРЕМЕННЫЕ СОСТОЯНИЯ ---

    private long lastAttackTime = 0;
    private LivingEntity targetEntity = null;
    private double lastDamageDealt = 0;
    private final Random random = new Random();

    // --- ENUM'Ы ---

    public enum TargetMode {
        Nearest("Ближайший"),
        LowestHP("Самый слабый (по HP)"),
        HighestHP("Самый сильный (по HP)"),
        Crosshair("По прицелу"),
        Random("Случайный"),
        Farthest("Самый дальний");

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
        All("Все существа"),
        PlayersOnly("Только игроки"),
        MobsOnly("Только мобы"),
        PassiveOnly("Только пассивные"),
        HostileOnly("Только враждебные");

        private final String name;

        TargetFilter(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // --- КОНСТРУКТОР ---

    public MaceDMG() {
        super(HM_CORE.CATEGORY, "MaceDMG", "Наносит огромный урон булавой (настройка до 1000 блоков)");
    }

    // --- ЖИЗНЕННЫЙ ЦИКЛ МОДУЛЯ ---

    @Override
    public void onActivate() {
        lastAttackTime = 0;
        targetEntity = null;
        lastDamageDealt = 0;
    }

    @Override
    public void onDeactivate() {
        targetEntity = null;
    }

    // --- ОСНОВНАЯ ЛОГИКА АТАКИ ---

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (onlyOnGround.get() && !mc.player.isOnGround()) return;
        if (System.currentTimeMillis() - lastAttackTime < cooldown.get()) return;

        if (!hasMaceInHand()) {
            if (autoSwitch.get()) {
                if (!switchToMace()) return;
            } else {
                return;
            }
        }

        LivingEntity target;
        if (event.target != null && event.target instanceof LivingEntity) {
            target = (LivingEntity) event.target;
        } else {
            target = findTarget();
        }

        if (target == null) return;
        if (mc.player.distanceTo(target) > range.get()) return;
        if (target.isDead() || target.getHealth() <= 0) return;
        if (!isTargetValid(target)) return;

        // --- СИМУЛЯЦИЯ ПАДЕНИЯ С НАСТРАИВАЕМОЙ ВЫСОТОЙ ---
        double currentY = mc.player.getY();
        double fakeY = currentY + Math.sqrt(fallHeight.get()); // Используем настройку

        for (int i = 0; i < packetCount.get(); i++) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), fakeY, mc.player.getZ(), true
            ));
        }

        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), currentY, mc.player.getZ(), true
        ));

        if (criticalMode.get()) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), currentY + 0.1, mc.player.getZ(), false
            ));
            mc.player.onCriticalHit(target);
        }

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        targetEntity = target;
        lastDamageDealt = calculateDamage(target);

        if (notify.get()) {
            String targetName = target instanceof PlayerEntity ?
                    ((PlayerEntity) target).getName().getString() :
                    target.getType().getName().getString();
            mc.player.sendMessage(Text.literal("§c⚔ §6" + targetName +
                    " §7получил §c" + String.format("%.1f", lastDamageDealt) + "§7 урона!"), false);
        }

        lastAttackTime = System.currentTimeMillis();
    }

    // --- ПОИСК ЦЕЛИ ---

    private LivingEntity findTarget() {
        if (mc.player == null || mc.world == null) return null;

        List<LivingEntity> candidates = new ArrayList<>();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living == mc.player) continue;
            if (ignoreDead.get() && (living.isDead() || living.getHealth() <= 0)) continue;
            if (ignoreInvisible.get() && living.isInvisible()) continue;
            if (mc.player.distanceTo(living) > range.get()) continue;
            if (!throughWalls.get() && !mc.player.canSee(living)) continue;
            if (!isTargetValid(living)) continue;

            candidates.add(living);
        }

        if (candidates.isEmpty()) return null;

        switch (targetMode.get()) {
            case Nearest:
                candidates.sort(Comparator.comparingDouble(e -> mc.player.distanceTo(e)));
                break;
            case LowestHP:
                candidates.sort(Comparator.comparingDouble(LivingEntity::getHealth));
                break;
            case HighestHP:
                candidates.sort((a, b) -> Double.compare(b.getHealth(), a.getHealth()));
                break;
            case Farthest:
                candidates.sort((a, b) -> Double.compare(mc.player.distanceTo(b), mc.player.distanceTo(a)));
                break;
            case Random:
                return candidates.get(random.nextInt(candidates.size()));
            case Crosshair:
                candidates.sort(Comparator.comparingDouble(e -> {
                    return mc.player.getRotationVector().distanceTo(e.getPos().subtract(mc.player.getPos()).normalize());
                }));
                break;
        }

        return candidates.get(0);
    }

    // --- ПРОВЕРКА ФИЛЬТРОВ ---

    private boolean isTargetValid(LivingEntity target) {
        if (target == null) return false;

        switch (targetFilter.get()) {
            case PlayersOnly:
                if (!(target instanceof PlayerEntity)) return false;
                break;
            case MobsOnly:
                if (!(target instanceof MobEntity)) return false;
                break;
            case PassiveOnly:
                if (!(target instanceof PassiveEntity)) return false;
                break;
            case HostileOnly:
                if (!(target instanceof MobEntity && ((MobEntity) target).isHostile())) return false;
                break;
            case All:
                break;
        }

        if (ignoreFriends.get() && target instanceof PlayerEntity) {
            String name = ((PlayerEntity) target).getName().getString();
            for (String friend : friendsList.get()) {
                if (name.equalsIgnoreCase(friend.trim())) {
                    return false;
                }
            }
        }

        return true;
    }

    // --- ПРОВЕРКА БУЛАВЫ В РУКЕ ---

    private boolean hasMaceInHand() {
        ItemStack mainHand = mc.player.getMainHandStack();
        ItemStack offHand = mc.player.getOffHandStack();

        if (mainHand.getItem() == Items.MACE) return true;
        if (useOffhand.get() && offHand.getItem() == Items.MACE) return true;

        return false;
    }

    // --- АВТОПЕРЕКЛЮЧЕНИЕ НА БУЛАВУ ---

    private boolean switchToMace() {
        if (mc.player == null) return false;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.MACE) {
                mc.player.getInventory().selectedSlot = i;
                return true;
            }
        }

        return false;
    }

    // --- РАСЧЁТ УРОНА С УЧЁТОМ ВЫСОТЫ ---

    private double calculateDamage(LivingEntity target) {
        double baseDamage = 6.0;
        double fallBonus = (Math.sqrt(fallHeight.get()) - 1) * 2;
        double totalDamage = baseDamage + fallBonus;

        if (target instanceof PlayerEntity) {
            totalDamage *= 0.7;
        }

        return Math.max(0, totalDamage);
    }

    // --- ВИЗУАЛИЗАЦИЯ (ЗАГЛУШКА) ---

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (renderRange.get() && mc.player != null) {
            // Здесь можно отрисовать сферу радиуса
        }
    }
}
