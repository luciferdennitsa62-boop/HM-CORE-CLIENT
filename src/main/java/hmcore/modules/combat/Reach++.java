package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.entity.player.InteractEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import hmcore.HM_CORE;

public class Reach extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTarget = settings.createGroup("Target");
    private final SettingGroup sgVisual = settings.createGroup("Visual");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Double> distance = sgGeneral.add(new DoubleSetting.Builder()
            .name("distance")
            .description("Дистанция атаки/взаимодействия (блоки)")
            .defaultValue(6.0)
            .min(3.0)
            .max(10.0)
            .sliderMax(10.0)
            .build()
    );

    public final Setting<Double> blockReach = sgGeneral.add(new DoubleSetting.Builder()
            .name("block-reach")
            .description("Дистанция взаимодействия с блоками")
            .defaultValue(6.0)
            .min(3.0)
            .max(10.0)
            .sliderMax(10.0)
            .build()
    );

    public final Setting<Boolean> onlyWhenSwing = sgGeneral.add(new BoolSetting.Builder()
            .name("only-when-swing")
            .description("Работает только при замахе (снижает риск)")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> onlyOnGround = sgGeneral.add(new BoolSetting.Builder()
            .name("only-on-ground")
            .description("Работает только на земле")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> whileSprinting = sgGeneral.add(new BoolSetting.Builder()
            .name("while-sprinting")
            .description("Работает только при спринте")
            .defaultValue(false)
            .build()
    );

    // --- НАСТРОЙКИ ЦЕЛИ ---

    public final Setting<Boolean> playersOnly = sgTarget.add(new BoolSetting.Builder()
            .name("players-only")
            .description("Увеличенная дистанция только для игроков")
            .defaultValue(false)
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

    public final Setting<Boolean> throughWalls = sgTarget.add(new BoolSetting.Builder()
            .name("through-walls")
            .description("Атака сквозь стены")
            .defaultValue(false)
            .build()
    );

    // --- ВИЗУАЛ ---

    public final Setting<Boolean> renderRange = sgVisual.add(new BoolSetting.Builder()
            .name("render-range")
            .description("Показывать радиус атаки")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> showHitbox = sgVisual.add(new BoolSetting.Builder()
            .name("show-hitbox")
            .description("Показывать увеличенный хитбокс цели")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> notify = sgVisual.add(new BoolSetting.Builder()
            .name("notify")
            .description("Уведомлять при атаке на дистанции > 3 блоков")
            .defaultValue(false)
            .build()
    );

    // --- ПЕРЕМЕННЫЕ ---
    private double currentReach = 3.0;
    private Entity targetEntity = null;

    // --- КОНСТРУКТОР ---
    public Reach() {
        super(HM_CORE.CATEGORY, "Reach++", "Увеличенная дистанция атаки и взаимодействия");
    }

    @Override
    public void onActivate() {
        currentReach = 3.0;
        targetEntity = null;
        if (notify.get()) {
            ChatUtils.info("Reach++ включен. Дистанция: " + distance.get() + " блоков");
        }
    }

    // --- ОБРАБОТКА АТАКИ ПО СУЩНОСТИ ---
    @EventHandler
    private void onInteractEntity(InteractEntityEvent event) {
        if (mc.player == null || mc.world == null) return;

        // Проверка на земле
        if (onlyOnGround.get() && !mc.player.isOnGround()) return;

        // Проверка спринта
        if (whileSprinting.get() && !mc.player.isSprinting()) return;

        // Проверка замаха
        if (onlyWhenSwing.get() && !mc.player.isSwinging()) return;

        // Проверка цели
        if (!(event.target instanceof LivingEntity target)) return;

        // Проверка на игрока
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

        // Проверка дистанции
        double dist = mc.player.distanceTo(target);
        if (dist > distance.get()) return;

        // Проверка сквозь стены
        if (!throughWalls.get() && !mc.player.canSee(target)) return;

        // Если дистанция больше 3 блоков — увеличиваем хитбокс
        if (dist > 3.0) {
            currentReach = distance.get();
            targetEntity = target;

            // Уведомление
            if (notify.get()) {
                ChatUtils.info("§7Атака на дистанции: §6" + String.format("%.1f", dist) + " §7блоков");
            }

            // Расширяем хитбокс цели
            Box originalBox = target.getBoundingBox();
            double expand = dist - 3.0; // На сколько расширить
            Box newBox = originalBox.expand(expand / 2);
            target.setBoundingBox(newBox);
        }
    }

    // --- ВОЗВРАТ ХИТБОКСА В НОРМАЛЬНОЕ СОСТОЯНИЕ ---
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (targetEntity != null && mc.player != null) {
            double dist = mc.player.distanceTo(targetEntity);
            if (dist > distance.get() || dist <= 3.0) {
                // Возвращаем нормальный хитбокс
                targetEntity.setBoundingBox(targetEntity.getBoundingBox().contract(0));
                targetEntity = null;
                currentReach = 3.0;
            }
        }
    }

    @Override
    public void onDeactivate() {
        if (targetEntity != null) {
            targetEntity.setBoundingBox(targetEntity.getBoundingBox().contract(0));
            targetEntity = null;
        }
        currentReach = 3.0;
    }

    @Override
    public String getInfoString() {
        return String.format("%.1f", distance.get()) + " блоков";
    }
}
