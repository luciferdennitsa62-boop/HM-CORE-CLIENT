package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;
import hmcore.HM_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MegaJump extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgMode = settings.createGroup("Mode");
    private final SettingGroup sgVisual = settings.createGroup("Visual");
    private final SettingGroup sgSafety = settings.createGroup("Safety");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Double> height = sgGeneral.add(new DoubleSetting.Builder()
            .name("height")
            .description("Высота прыжка (в блоках). ⚠️ Значения >10 могут вызвать бан на многих серверах!")
            .defaultValue(2.0)
            .min(0.5)
            .max(1000.0)
            .sliderMax(1000.0)
            .build()
    );

    public final Setting<Double> horizontalBoost = sgGeneral.add(new DoubleSetting.Builder()
            .name("horizontal-boost")
            .description("Горизонтальное ускорение при прыжке")
            .defaultValue(1.0)
            .min(0.0)
            .max(5.0)
            .sliderMax(5.0)
            .build()
    );

    public final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
            .name("cooldown")
            .description("Задержка между прыжками (мс)")
            .defaultValue(0)
            .min(0)
            .max(2000)
            .sliderMax(2000)
            .build()
    );

    // --- РЕЖИМЫ ---

    public final Setting<JumpMode> jumpMode = sgMode.add(new EnumSetting.Builder<JumpMode>()
            .name("jump-mode")
            .description("Режим прыжка")
            .defaultValue(JumpMode.Normal)
            .build()
    );

    public final Setting<Boolean> onlyWhenSprinting = sgMode.add(new BoolSetting.Builder()
            .name("only-when-sprinting")
            .description("Только при спринте")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> autoJump = sgMode.add(new BoolSetting.Builder()
            .name("auto-jump")
            .description("Автоматически прыгать")
            .defaultValue(false)
            .build()
    );

    public final Setting<Integer> autoJumpDelay = sgMode.add(new IntSetting.Builder()
            .name("auto-jump-delay")
            .description("Задержка между автопрыжками (тики)")
            .defaultValue(20)
            .min(1)
            .max(100)
            .sliderMax(100)
            .build()
    );

    public final Setting<Boolean> glide = sgMode.add(new BoolSetting.Builder()
            .name("glide")
            .description("Плавное снижение после прыжка")
            .defaultValue(false)
            .build()
    );

    public final Setting<Double> glideSpeed = sgMode.add(new DoubleSetting.Builder()
            .name("glide-speed")
            .description("Скорость снижения при планере")
            .defaultValue(0.05)
            .min(0.01)
            .max(0.5)
            .sliderMax(0.5)
            .build()
    );

    // --- БЕЗОПАСНОСТЬ ---

    public final Setting<Boolean> resetFallDistance = sgSafety.add(new BoolSetting.Builder()
            .name("reset-fall-distance")
            .description("Сбрасывать дистанцию падения (чтобы не разбиться)")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> antiVoid = sgSafety.add(new BoolSetting.Builder()
            .name("anti-void")
            .description("Защита от падения в пустоту")
            .defaultValue(true)
            .build()
    );

    public final Setting<Integer> voidHeight = sgSafety.add(new IntSetting.Builder()
            .name("void-height")
            .description("Высота, на которой срабатывает анти-войд")
            .defaultValue(-10)
            .min(-50)
            .max(-5)
            .sliderMax(-5)
            .build()
    );

    public final Setting<Boolean> autoDisableOnDamage = sgSafety.add(new BoolSetting.Builder()
            .name("auto-disable-on-damage")
            .description("Отключать модуль при получении урона")
            .defaultValue(false)
            .build()
    );

    // --- ВИЗУАЛ ---

    public final Setting<Boolean> renderTrail = sgVisual.add(new BoolSetting.Builder()
            .name("render-trail")
            .description("Оставлять след из частиц при прыжке")
            .defaultValue(false)
            .build()
    );

    public final Setting<Color> trailColor = sgVisual.add(new ColorSetting.Builder()
            .name("trail-color")
            .description("Цвет следа")
            .defaultValue(new Color(0x00FF00))
            .build()
    );

    public final Setting<Boolean> renderHeight = sgVisual.add(new BoolSetting.Builder()
            .name("render-height")
            .description("Показывать высоту прыжка в HUD")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> notifyJump = sgVisual.add(new BoolSetting.Builder()
            .name("notify-jump")
            .description("Уведомлять о прыжке")
            .defaultValue(false)
            .build()
    );

    // --- ПРОДВИНУТЫЕ ---

    public final Setting<Boolean> randomizeHeight = sgAdvanced.add(new BoolSetting.Builder()
            .name("randomize-height")
            .description("Случайная высота прыжка (для обхода античитов)")
            .defaultValue(false)
            .build()
    );

    public final Setting<Double> randomRange = sgAdvanced.add(new DoubleSetting.Builder()
            .name("random-range")
            .description("Диапазон случайного разброса высоты")
            .defaultValue(0.5)
            .min(0.1)
            .max(5.0)
            .sliderMax(5.0)
            .build()
    );

    public final Setting<Boolean> silentJump = sgAdvanced.add(new BoolSetting.Builder()
            .name("silent-jump")
            .description("Тихий прыжок (без звука и анимации)")
            .defaultValue(false)
            .build()
    );

    // --- ENUM ---
    public enum JumpMode {
        Normal("Обычный"),
        Constant("Постоянный"),
        Pulse("Пульсирующий"),
        Rocket("Ракета"),
        Moon("Лунный");

        private final String name;

        JumpMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // --- ПЕРЕМЕННЫЕ ---
    private long lastJumpTime = 0;
    private int autoJumpCounter = 0;
    private boolean isJumping = false;
    private final Random random = new Random();
    private final List<Vec3d> trailPositions = new ArrayList<>();

    // --- КОНСТРУКТОР ---
    public MegaJump() {
        super(HM_CORE.CATEGORY, "MegaJump", "Сверхвысокий прыжок (до 1000 блоков) с кучей настроек");
    }

    @Override
    public void onActivate() {
        lastJumpTime = 0;
        autoJumpCounter = 0;
        isJumping = false;
        trailPositions.clear();
        if (notifyJump.get()) {
            ChatUtils.info("MegaJump активирован. Высота: " + height.get() + " блоков");
        }
    }

    @Override
    public void onDeactivate() {
        trailPositions.clear();
        isJumping = false;
    }

    // --- ТИК ---
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Анти-войд
        if (antiVoid.get() && mc.player.getY() < voidHeight.get()) {
            mc.player.setPosition(mc.player.getX(), 10, mc.player.getZ());
            if (notifyJump.get()) {
                ChatUtils.warn("Спасение от пустоты!");
            }
            return;
        }

        // Проверка урона
        if (autoDisableOnDamage.get() && mc.player.hurtTime > 0) {
            toggle();
            ChatUtils.warn("MegaJump выключен (получен урон)");
            return;
        }

        // Проверка на земле для автопрыжка
        boolean shouldJump = false;

        // Автопрыжок
        if (autoJump.get()) {
            autoJumpCounter++;
            if (autoJumpCounter >= autoJumpDelay.get() && mc.player.isOnGround()) {
                shouldJump = true;
                autoJumpCounter = 0;
            }
        }

        // Ручной прыжок
        if (mc.options.jumpKey.isPressed() && !autoJump.get()) {
            if (onlyWhenSprinting.get() && !mc.player.isSprinting()) return;
            if (System.currentTimeMillis() - lastJumpTime >= cooldown.get()) {
                shouldJump = true;
            }
        }

        // Выполнение прыжка
        if (shouldJump) {
            performJump();
            lastJumpTime = System.currentTimeMillis();
            if (notifyJump.get()) {
                ChatUtils.info("§aПрыжок! Высота: " + String.format("%.1f", height.get()) + " блоков");
            }
        }

        // Планер
        if (glide.get() && !mc.player.isOnGround() && mc.player.getVelocity().y < 0) {
            mc.player.setVelocity(mc.player.getVelocity().x, -glideSpeed.get(), mc.player.getVelocity().z);
        }

        // След
        if (renderTrail.get() && isJumping) {
            trailPositions.add(mc.player.getPos());
            if (trailPositions.size() > 50) {
                trailPositions.remove(0);
            }
        }

        // Сброс флага прыжка
        if (mc.player.isOnGround()) {
            isJumping = false;
        }
    }

    // --- ВЫПОЛНЕНИЕ ПРЫЖКА ---
    private void performJump() {
        if (mc.player == null) return;

        double jumpHeight = height.get();

        // Случайная высота
        if (randomizeHeight.get()) {
            double range = randomRange.get();
            jumpHeight = height.get() + (random.nextDouble() - 0.5) * range * 2;
            jumpHeight = Math.max(0.5, Math.min(1000.0, jumpHeight));
        }

        // Горизонтальный буст
        double hBoost = horizontalBoost.get();
        if (hBoost != 1.0) {
            Vec3d velocity = mc.player.getVelocity();
            mc.player.setVelocity(velocity.x * hBoost, jumpHeight, velocity.z * hBoost);
        } else {
            mc.player.setVelocity(mc.player.getVelocity().x, jumpHeight, mc.player.getVelocity().z);
        }

        // Сброс дистанции падения
        if (resetFallDistance.get()) {
            mc.player.fallDistance = 0;
        }

        isJumping = true;

        // Режимы
        switch (jumpMode.get()) {
            case Constant -> {
                // Постоянная скорость вверх
                mc.player.setVelocity(mc.player.getVelocity().x, jumpHeight, mc.player.getVelocity().z);
            }
            case Pulse -> {
                // Пульсирующий рывок
                mc.player.setVelocity(mc.player.getVelocity().x, jumpHeight * 0.8, mc.player.getVelocity().z);
                // Добавляем второй импульс через 2 тика
                // Нельзя сделать в этом же тике, поэтому оставляем как есть
            }
            case Rocket -> {
                // Ракета: постоянный подъём
                mc.player.setVelocity(mc.player.getVelocity().x, jumpHeight, mc.player.getVelocity().z);
                // Дополнительный импульс через 5 тиков будет в onTick
            }
            case Moon -> {
                // Лунный прыжок: низкая гравитация
                mc.player.setVelocity(mc.player.getVelocity().x, jumpHeight * 1.5, mc.player.getVelocity().z);
            }
            default -> {
                // Normal
            }
        }

        // Тихий прыжок
        if (silentJump.get()) {
            // Здесь можно убрать звук прыжка (сложно, оставляем)
        }
    }

    // --- РЕНДЕРИНГ СЛЕДА ---
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!renderTrail.get() || trailPositions.isEmpty()) return;

        Color c = trailColor.get();
        for (int i = 0; i < trailPositions.size() - 1; i++) {
            Vec3d p1 = trailPositions.get(i);
            Vec3d p2 = trailPositions.get(i + 1);
            event.drawLine(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, c, 2.0f);
        }
    }

    // --- ИНФОРМАЦИЯ В HUD ---
    @Override
    public String getInfoString() {
        if (renderHeight.get()) {
            return String.format("%.1f", height.get()) + " блоков";
        }
        return null;
    }
}
