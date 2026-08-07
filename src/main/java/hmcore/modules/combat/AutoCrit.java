package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import hmcore.HM_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AutoCrit extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWeapon = settings.createGroup("Weapon");
    private final SettingGroup sgTarget = settings.createGroup("Target");
    private final SettingGroup sgVisual = settings.createGroup("Visual");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Режим нанесения критического удара")
            .defaultValue(Mode.Packet)
            .build()
    );

    public final Setting<Double> jumpHeight = sgGeneral.add(new DoubleSetting.Builder()
            .name("jump-height")
            .description("Высота прыжка для режима MiniJump")
            .defaultValue(0.1)
            .min(0.05)
            .max(0.5)
            .sliderMax(0.5)
            .build()
    );

    public final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
            .name("delay")
            .description("Задержка между критическими ударами (мс)")
            .defaultValue(0)
            .min(0)
            .max(500)
            .sliderMax(500)
            .build()
    );

    public final Setting<Boolean> onlyOnGround = sgGeneral.add(new BoolSetting.Builder()
            .name("only-on-ground")
            .description("Работает только когда игрок на земле")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> autoJump = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-jump")
            .description("Автоматически прыгать перед атакой (для режимов Jump и MiniJump)")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> onlyWhenMoving = sgGeneral.add(new BoolSetting.Builder()
            .name("only-when-moving")
            .description("Работает только когда игрок двигается")
            .defaultValue(false)
            .build()
    );

    // --- НАСТРОЙКИ ОРУЖИЯ ---

    public final Setting<WeaponFilter> weaponFilter = sgWeapon.add(new EnumSetting.Builder<WeaponFilter>()
            .name("weapon-filter")
            .description("Какое оружие использовать для крита")
            .defaultValue(WeaponFilter.All)
            .build()
    );

    public final Setting<Boolean> onlyMainHand = sgWeapon.add(new BoolSetting.Builder()
            .name("only-main-hand")
            .description("Крит только из основной руки")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> autoSwitch = sgWeapon.add(new BoolSetting.Builder()
            .name("auto-switch")
            .description("Автоматически переключаться на лучшее оружие для крита")
            .defaultValue(false)
            .build()
    );

    // --- НАСТРОЙКИ ЦЕЛИ ---

    public final Setting<Double> range = sgTarget.add(new DoubleSetting.Builder()
            .name("range")
            .description("Максимальная дистанция до цели")
            .defaultValue(4.5)
            .min(1.0)
            .max(8.0)
            .sliderMax(8.0)
            .build()
    );

    public final Setting<SortPriority> targetPriority = sgTarget.add(new EnumSetting.Builder<SortPriority>()
            .name("target-priority")
            .description("Приоритет выбора цели")
            .defaultValue(SortPriority.LowestHealth)
            .build()
    );

    public final Setting<Boolean> playersOnly = sgTarget.add(new BoolSetting.Builder()
            .name("players-only")
            .description("Атаковать только игроков")
            .defaultValue(true)
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

    // --- ВИЗУАЛЬНЫЕ НАСТРОЙКИ ---

    public final Setting<Boolean> notify = sgVisual.add(new BoolSetting.Builder()
            .name("notify")
            .description("Уведомлять в чат о критическом ударе")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> renderParticles = sgVisual.add(new BoolSetting.Builder()
            .name("render-particles")
            .description("Показывать частицы критического удара (эффект)")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> showDamage = sgVisual.add(new BoolSetting.Builder()
            .name("show-damage")
            .description("Показывать расчётный урон над целью")
            .defaultValue(false)
            .build()
    );

    // --- ПРОДВИНУТЫЕ НАСТРОЙКИ ---

    public final Setting<Integer> packetCount = sgAdvanced.add(new IntSetting.Builder()
            .name("packet-count")
            .description("Количество пакетов для отправки (Packet режим)")
            .defaultValue(2)
            .min(1)
            .max(10)
            .sliderMax(10)
            .build()
    );

    public final Setting<Boolean> silentRotate = sgAdvanced.add(new BoolSetting.Builder()
            .name("silent-rotate")
            .description("Поворачивать голову к цели (незаметно для других)")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> onlyWhenTargetVisible = sgAdvanced.add(new BoolSetting.Builder()
            .name("only-when-target-visible")
            .description("Крит только когда цель видна")
            .defaultValue(false)
            .build()
    );

    // --- ВНУТРЕННИЕ ПЕРЕМЕННЫЕ ---

    private long lastCritTime = 0;
    private final Random random = new Random();
    private LivingEntity targetEntity = null;

    // --- ENUM'Ы ---

    public enum Mode {
        Packet("Пакетный (без прыжка)"),
        MiniJump("Микропрыжок"),
        Jump("Прыжок"),
        Grim("Grim (спец. значения)"),
        Legit("Легитный (только прыжок)");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum WeaponFilter {
        All("Всё оружие"),
        SwordsOnly("Только мечи"),
        AxesOnly("Только топоры"),
        SwordsAndAxes("Мечи и топоры");

        private final String name;

        WeaponFilter(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // --- КОНСТРУКТОР ---

    public AutoCrit() {
        super(HM_CORE.CATEGORY, "AutoCrit", "Автоматически наносит критические удары с кучей настроек");
    }

    @Override
    public void onActivate() {
        lastCritTime = 0;
        targetEntity = null;
    }

    @Override
    public void onDeactivate() {
        targetEntity = null;
    }

    // --- ОСНОВНАЯ ЛОГИКА ---

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        if (mc.player == null || mc.world == null) return;

        // Проверка на земле
        if (onlyOnGround.get() && !mc.player.isOnGround()) return;

        // Проверка движения
        if (onlyWhenMoving.get() && mc.player.getVelocity().length() < 0.01) return;

        // Задержка
        if (System.currentTimeMillis() - lastCritTime < delay.get()) return;

        // Проверка оружия
        if (!isWeaponValid()) {
            if (autoSwitch.get()) {
                if (!switchToBestWeapon()) return;
            } else {
                return;
            }
        }

        // Определяем цель
        LivingEntity target;
        if (event.target != null && event.target instanceof LivingEntity) {
            target = (LivingEntity) event.target;
        } else {
            target = findTarget();
        }

        if (target == null) return;
        if (mc.player.distanceTo(target) > range.get()) return;
        if (target.isDead() || target.getHealth() <= 0) return;
        if (onlyWhenTargetVisible.get() && !mc.player.canSee(target)) return;
        if (!isTargetValid(target)) return;

        // --- НАНЕСЕНИЕ КРИТИЧЕСКОГО УДАРА ---

        // Сохраняем цель для визуала
        targetEntity = target;

        // Выполняем критический удар в зависимости от режима
        boolean critSuccess = performCrit();

        if (critSuccess) {
            // Уведомление
            if (notify.get()) {
                String targetName = target instanceof PlayerEntity ?
                        ((PlayerEntity) target).getName().getString() :
                        target.getType().getName().getString();
                mc.player.sendMessage(Text.literal("§c⚔ §6" + targetName +
                        " §7получил §cКРИТИЧЕСКИЙ§7 удар!"), false);
            }

            // Частицы
            if (renderParticles.get()) {
                mc.player.onCriticalHit(target);
            }

            lastCritTime = System.currentTimeMillis();
        }
    }

    // --- ВЫПОЛНЕНИЕ КРИТИЧЕСКОГО УДАРА ---

    private boolean performCrit() {
        if (mc.player == null) return false;

        switch (mode.get()) {
            case Packet -> {
                double y = mc.player.getY();
                for (int i = 0; i < packetCount.get(); i++) {
                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                            mc.player.getX(), y + 0.11, mc.player.getZ(), false));
                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                            mc.player.getX(), y, mc.player.getZ(), false));
                }
                return true;
            }
            case MiniJump -> {
                if (autoJump.get()) {
                    mc.player.setVelocity(mc.player.getVelocity().x, jumpHeight.get(), mc.player.getVelocity().z);
                    mc.player.fallDistance = 0.1f;
                    mc.player.onGround = false;
                    return true;
                } else {
                    // Вручную прыгаем
                    mc.player.jump();
                    return true;
                }
            }
            case Jump -> {
                if (autoJump.get()) {
                    mc.player.jump();
                    return true;
                } else {
                    // Не прыгаем, только наносим урон (обычный)
                    return false;
                }
            }
            case Grim -> {
                double y = mc.player.getY();
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        mc.player.getX(), y + 0.001091981, mc.player.getZ(), true));
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        mc.player.getX(), y + 0.000114514, mc.player.getZ(), false));
                return true;
            }
            case Legit -> {
                // Легитный крит: прыгаем и бьём, но только если цель рядом
                if (autoJump.get()) {
                    mc.player.jump();
                    return true;
                } else {
                    mc.player.jump();
                    return true;
                }
            }
            default -> {
                return false;
            }
        }
    }

    // --- ПРОВЕРКА ОРУЖИЯ ---

    private boolean isWeaponValid() {
        if (mc.player == null) return false;

        ItemStack mainHand = mc.player.getMainHandStack();
        ItemStack offHand = mc.player.getOffHandStack();

        ItemStack weapon = onlyMainHand.get() ? mainHand : offHand;
        if (weapon.isEmpty()) return false;

        switch (weaponFilter.get()) {
            case SwordsOnly -> {
                return weapon.getItem() == Items.DIAMOND_SWORD ||
                        weapon.getItem() == Items.NETHERITE_SWORD ||
                        weapon.getItem() == Items.IRON_SWORD ||
                        weapon.getItem() == Items.GOLDEN_SWORD ||
                        weapon.getItem() == Items.STONE_SWORD ||
                        weapon.getItem() == Items.WOODEN_SWORD;
            }
            case AxesOnly -> {
                return weapon.getItem() == Items.DIAMOND_AXE ||
                        weapon.getItem() == Items.NETHERITE_AXE ||
                        weapon.getItem() == Items.IRON_AXE ||
                        weapon.getItem() == Items.GOLDEN_AXE ||
                        weapon.getItem() == Items.STONE_AXE ||
                        weapon.getItem() == Items.WOODEN_AXE;
            }
            case SwordsAndAxes -> {
                return isSword(weapon.getItem()) || isAxe(weapon.getItem());
            }
            case All -> {
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    private boolean isSword(Item item) {
        return item == Items.DIAMOND_SWORD || item == Items.NETHERITE_SWORD ||
                item == Items.IRON_SWORD || item == Items.GOLDEN_SWORD ||
                item == Items.STONE_SWORD || item == Items.WOODEN_SWORD;
    }

    private boolean isAxe(Item item) {
        return item == Items.DIAMOND_AXE || item == Items.NETHERITE_AXE ||
                item == Items.IRON_AXE || item == Items.GOLDEN_AXE ||
                item == Items.STONE_AXE || item == Items.WOODEN_AXE;
    }

    // --- АВТОПЕРЕКЛЮЧЕНИЕ НА ЛУЧШЕЕ ОРУЖИЕ ---

    private boolean switchToBestWeapon() {
        if (mc.player == null) return false;

        // Ищем в инвентаре меч или топор
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isSword(stack.getItem()) || isAxe(stack.getItem())) {
                mc.player.getInventory().selectedSlot = i;
                return true;
            }
        }
        return false;
    }

    // --- ПОИСК ЦЕЛИ ---

    private LivingEntity findTarget() {
        if (mc.player == null || mc.world == null) return null;

        List<LivingEntity> candidates = new ArrayList<>();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living == mc.player) continue;
            if (living.isDead() || living.getHealth() <= 0) continue;
            if (playersOnly.get() && !(living instanceof PlayerEntity)) continue;
            if (mc.player.distanceTo(living) > range.get()) continue;
            if (onlyWhenTargetVisible.get() && !mc.player.canSee(living)) continue;
            if (!isTargetValid(living)) continue;

            candidates.add(living);
        }

        if (candidates.isEmpty()) return null;

        // Сортировка по приоритету
        switch (targetPriority.get()) {
            case LowestHealth:
                candidates.sort((a, b) -> Float.compare(a.getHealth(), b.getHealth()));
                break;
            case HighestHealth:
                candidates.sort((a, b) -> Float.compare(b.getHealth(), a.getHealth()));
                break;
            case Closest:
                candidates.sort((a, b) -> Double.compare(mc.player.distanceTo(a), mc.player.distanceTo(b)));
                break;
            case Farthest:
                candidates.sort((a, b) -> Double.compare(mc.player.distanceTo(b), mc.player.distanceTo(a)));
                break;
            default:
                break;
        }

        return candidates.get(0);
    }

    // --- ПРОВЕРКА ЦЕЛИ НА ФИЛЬТРЫ ---

    private boolean isTargetValid(LivingEntity target) {
        if (target == null) return false;

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

    // --- ВИЗУАЛИЗАЦИЯ (ЗАГЛУШКА) ---

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (showDamage.get() && targetEntity != null && mc.player != null) {
            // Здесь можно отобразить урон над целью
            // Сложно без рендеринга, но можно добавить позже
        }
    }
}
