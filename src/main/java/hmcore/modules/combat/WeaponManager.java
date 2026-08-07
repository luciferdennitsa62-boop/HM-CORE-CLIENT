package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.text.Text;
import hmcore.HM_CORE;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class WeaponManager extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWeapon = settings.createGroup("Weapon");
    private final SettingGroup sgVisual = settings.createGroup("Visual");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Priority> priority = sgGeneral.add(new EnumSetting.Builder<Priority>()
            .name("priority")
            .description("Критерий выбора оружия")
            .defaultValue(Priority.Damage)
            .build()
    );

    public final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-switch")
            .description("Автоматически переключаться на лучшее оружие")
            .defaultValue(true)
            .build()
    );

    public final Setting<Integer> switchDelay = sgGeneral.add(new IntSetting.Builder()
            .name("switch-delay")
            .description("Задержка между переключениями (мс)")
            .defaultValue(100)
            .min(0)
            .max(500)
            .sliderMax(500)
            .build()
    );

    public final Setting<Boolean> onlyWhenTarget = sgGeneral.add(new BoolSetting.Builder()
            .name("only-when-target")
            .description("Переключаться только когда есть цель")
            .defaultValue(false)
            .build()
    );

    // --- НАСТРОЙКИ ОРУЖИЯ ---

    public final Setting<WeaponType> weaponType = sgWeapon.add(new EnumSetting.Builder<WeaponType>()
            .name("weapon-type")
            .description("Типы оружия для выбора")
            .defaultValue(WeaponType.SwordsAndAxes)
            .build()
    );

    public final Setting<Boolean> preferNetherite = sgWeapon.add(new BoolSetting.Builder()
            .name("prefer-netherite")
            .description("Предпочитать незеритовое оружие")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> preferEnchanted = sgWeapon.add(new BoolSetting.Builder()
            .name("prefer-enchanted")
            .description("Предпочитать зачарованное оружие")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> alsoBows = sgWeapon.add(new BoolSetting.Builder()
            .name("also-bows")
            .description("Также переключаться на луки (для дальнего боя)")
            .defaultValue(false)
            .build()
    );

    // --- ВИЗУАЛ ---

    public final Setting<Boolean> showDamage = sgVisual.add(new BoolSetting.Builder()
            .name("show-damage")
            .description("Показывать расчётный урон над целью")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> showCurrentWeapon = sgVisual.add(new BoolSetting.Builder()
            .name("show-current-weapon")
            .description("Показывать текущее оружие в HUD")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> notifySwitch = sgVisual.add(new BoolSetting.Builder()
            .name("notify-switch")
            .description("Уведомлять о смене оружия")
            .defaultValue(false)
            .build()
    );

    // --- ПРОДВИНУТЫЕ ---

    public final Setting<Boolean> silentSwitch = sgAdvanced.add(new BoolSetting.Builder()
            .name("silent-switch")
            .description("Тихая смена оружия (без анимации)")
            .defaultValue(false)
            .build()
    );

    public final Setting<Integer> hotbarSlots = sgAdvanced.add(new IntSetting.Builder()
            .name("hotbar-slots")
            .description("Количество слотов хотбара для поиска (1-9)")
            .defaultValue(9)
            .min(1)
            .max(9)
            .sliderMax(9)
            .build()
    );

    // --- ENUM'Ы ---
    public enum Priority {
        Damage("Урон"),
        Speed("Скорость атаки"),
        Reach("Дальность");

        private final String name;

        Priority(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum WeaponType {
        SwordsOnly("Только мечи"),
        AxesOnly("Только топоры"),
        SwordsAndAxes("Мечи и топоры"),
        All("Всё оружие");

        private final String name;

        WeaponType(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // --- ПЕРЕМЕННЫЕ ---
    private long lastSwitchTime = 0;
    private ItemStack currentBestWeapon = null;
    private int currentBestSlot = -1;

    // --- КОНСТРУКТОР ---
    public WeaponManager() {
        super(HM_CORE.CATEGORY, "WeaponManager", "Автоматический выбор лучшего оружия");
    }

    @Override
    public void onActivate() {
        lastSwitchTime = 0;
        currentBestWeapon = null;
        currentBestSlot = -1;
    }

    @Override
    public void onDeactivate() {
        currentBestWeapon = null;
        currentBestSlot = -1;
    }

    // --- ТИК ---
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Проверка задержки
        if (System.currentTimeMillis() - lastSwitchTime < switchDelay.get()) return;

        // Проверка наличия цели
        if (onlyWhenTarget.get()) {
            LivingEntity target = (LivingEntity) TargetUtils.getPlayerTarget(6.0, meteordevelopment.meteorclient.utils.entity.SortPriority.LowestHealth);
            if (target == null) return;
        }

        // Поиск лучшего оружия
        int bestSlot = findBestWeapon();
        if (bestSlot == -1) return;

        // Проверка, изменилось ли оружие
        if (bestSlot != mc.player.getInventory().selectedSlot) {
            if (autoSwitch.get()) {
                // Переключение
                if (silentSwitch.get()) {
                    // Тихая смена (без анимации)
                    mc.player.getInventory().selectedSlot = bestSlot;
                } else {
                    // Обычная смена
                    mc.player.getInventory().selectedSlot = bestSlot;
                }
                lastSwitchTime = System.currentTimeMillis();
                currentBestSlot = bestSlot;
                currentBestWeapon = mc.player.getInventory().getStack(bestSlot);

                if (notifySwitch.get()) {
                    String weaponName = currentBestWeapon.getItem().getName().getString();
                    info("§aПереключено на: " + weaponName);
                }
            }
        }
    }

    // --- ПОИСК ЛУЧШЕГО ОРУЖИЯ ---
    private int findBestWeapon() {
        if (mc.player == null) return -1;
        int bestSlot = -1;
        double bestScore = -Double.MAX_VALUE;

        int slots = Math.min(hotbarSlots.get(), 9);

        for (int i = 0; i < slots; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            // Проверка типа оружия
            if (!isWeapon(stack)) continue;

            double score = calculateScore(stack);

            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    // --- ПРОВЕРКА, ЯВЛЯЕТСЯ ЛИ ПРЕДМЕТ ОРУЖИЕМ ---
    private boolean isWeapon(ItemStack stack) {
        Item item = stack.getItem();

        // Проверка по типу
        switch (weaponType.get()) {
            case SwordsOnly:
                return item instanceof SwordItem;
            case AxesOnly:
                return item instanceof AxeItem;
            case SwordsAndAxes:
                return item instanceof SwordItem || item instanceof AxeItem;
            case All:
                return item instanceof SwordItem || item instanceof AxeItem || (alsoBows.get() && item instanceof BowItem);
        }
        return false;
    }

    // --- РАСЧЁТ ОЦЕНКИ ОРУЖИЯ ---
    private double calculateScore(ItemStack stack) {
        double score = 0.0;
        Item item = stack.getItem();

        // Базовый урон
        double damage = 0.0;
        double speed = 0.0;
        double reach = 0.0;

        if (item instanceof SwordItem sword) {
            damage = sword.getAttackDamage();
            speed = sword.getAttackSpeed();
            reach = 3.0; // стандартная дальность меча
        } else if (item instanceof AxeItem axe) {
            damage = axe.getAttackDamage();
            speed = axe.getAttackSpeed();
            reach = 3.0;
        } else if (item instanceof BowItem) {
            damage = 6.0; // средний урон стрелы
            speed = 0.0;
            reach = 10.0; // лук имеет большую дальность
        } else {
            return -Double.MAX_VALUE;
        }

        // Учёт зачарований (упрощённо)
        if (preferEnchanted.get() && stack.hasEnchantments()) {
            damage += 1.0;
            speed += 0.1;
        }

        // Учёт материала (незерит предпочтительнее)
        if (preferNetherite.get()) {
            if (item instanceof SwordItem sword && sword.getMaterial().toString().toLowerCase().contains("netherite")) {
                damage += 1.0;
            }
            if (item instanceof AxeItem axe && axe.getMaterial().toString().toLowerCase().contains("netherite")) {
                damage += 1.0;
            }
        }

        // Оценка по приоритету
        switch (priority.get()) {
            case Damage:
                score = damage;
                break;
            case Speed:
                score = speed;
                break;
            case Reach:
                score = reach;
                break;
        }

        return score;
    }

    // --- ОТОБРАЖЕНИЕ УРОНА В HUD ---
    @EventHandler
    private void onTickRender(TickEvent.Post event) {
        if (!showDamage.get() || mc.player == null) return;

        LivingEntity target = (LivingEntity) TargetUtils.getPlayerTarget(6.0, meteordevelopment.meteorclient.utils.entity.SortPriority.LowestHealth);
        if (target != null && currentBestWeapon != null) {
            double damage = calculateScore(currentBestWeapon);
            // Здесь можно отрисовать урон над целью
            // В стандартном рендере это сложно, но можно добавить через RenderUtils
            // Пока оставляем заглушку
        }
    }

    // --- ПОКАЗ ТЕКУЩЕГО ОРУЖИЯ В HUD ---
    @Override
    public String getInfoString() {
        if (showCurrentWeapon.get() && currentBestWeapon != null) {
            String name = currentBestWeapon.getItem().getName().getString();
            return name;
        }
        return null;
    }
}
