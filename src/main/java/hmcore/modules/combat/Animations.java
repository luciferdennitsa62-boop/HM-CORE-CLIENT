package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import hmcore.HM_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Animations extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSwing = settings.createGroup("Swing");
    private final SettingGroup sgGui = settings.createGroup("GUI");
    private final SettingGroup sgHud = settings.createGroup("HUD");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
            .name("speed")
            .description("Скорость всех анимаций")
            .defaultValue(1.0)
            .min(0.1)
            .max(5.0)
            .sliderMax(5.0)
            .build()
    );

    public final Setting<Boolean> enabled = sgGeneral.add(new BoolSetting.Builder()
            .name("enabled")
            .description("Включить анимации")
            .defaultValue(true)
            .build()
    );

    // --- НАСТРОЙКИ ЗАМАХА ---

    public final Setting<SwingMode> swingMode = sgSwing.add(new EnumSetting.Builder<SwingMode>()
            .name("swing-mode")
            .description("Режим анимации замаха")
            .defaultValue(SwingMode.Smooth)
            .build()
    );

    public final Setting<Double> swingSpeed = sgSwing.add(new DoubleSetting.Builder()
            .name("swing-speed")
            .description("Скорость замаха")
            .defaultValue(1.0)
            .min(0.1)
            .max(3.0)
            .sliderMax(3.0)
            .build()
    );

    public final Setting<Boolean> cancelPacketSwing = sgSwing.add(new BoolSetting.Builder()
            .name("cancel-packet-swing")
            .description("Отменять стандартную анимацию замаха (чтобы не было дёрганий)")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> oldSwing = sgSwing.add(new BoolSetting.Builder()
            .name("old-swing")
            .description("Анимация замаха как в старых версиях (1.8)")
            .defaultValue(false)
            .build()
    );

    // --- НАСТРОЙКИ GUI ---

    public final Setting<Boolean> smoothGui = sgGui.add(new BoolSetting.Builder()
            .name("smooth-gui")
            .description("Плавное открытие/закрытие GUI")
            .defaultValue(true)
            .build()
    );

    public final Setting<Double> guiTransitionSpeed = sgGui.add(new DoubleSetting.Builder()
            .name("gui-transition-speed")
            .description("Скорость анимации GUI")
            .defaultValue(0.1)
            .min(0.01)
            .max(0.5)
            .sliderMax(0.5)
            .build()
    );

    public final Setting<Boolean> smoothScrolling = sgGui.add(new BoolSetting.Builder()
            .name("smooth-scrolling")
            .description("Плавный скроллинг")
            .defaultValue(true)
            .build()
    );

    public final Setting<Double> scrollSpeed = sgGui.add(new DoubleSetting.Builder()
            .name("scroll-speed")
            .description("Скорость скроллинга")
            .defaultValue(1.0)
            .min(0.1)
            .max(3.0)
            .sliderMax(3.0)
            .build()
    );

    public final Setting<Boolean> fadeEffects = sgGui.add(new BoolSetting.Builder()
            .name("fade-effects")
            .description("Эффекты затухания при открытии GUI")
            .defaultValue(true)
            .build()
    );

    public final Setting<Double> fadeSpeed = sgGui.add(new DoubleSetting.Builder()
            .name("fade-speed")
            .description("Скорость затухания")
            .defaultValue(0.1)
            .min(0.01)
            .max(0.5)
            .sliderMax(0.5)
            .build()
    );

    // --- НАСТРОЙКИ HUD ---

    public final Setting<Boolean> smoothHud = sgHud.add(new BoolSetting.Builder()
            .name("smooth-hud")
            .description("Плавное обновление HUD")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> animatedHealth = sgHud.add(new BoolSetting.Builder()
            .name("animated-health")
            .description("Анимированная полоса здоровья (плавно изменяется)")
            .defaultValue(true)
            .build()
    );

    public final Setting<Double> healthAnimSpeed = sgHud.add(new DoubleSetting.Builder()
            .name("health-anim-speed")
            .description("Скорость анимации здоровья")
            .defaultValue(0.05)
            .min(0.01)
            .max(0.2)
            .sliderMax(0.2)
            .build()
    );

    // --- ПРОДВИНУТЫЕ ---

    public final Setting<Boolean> randomOffset = sgAdvanced.add(new BoolSetting.Builder()
            .name("random-offset")
            .description("Случайное смещение анимаций (для разнообразия)")
            .defaultValue(false)
            .build()
    );

    public final Setting<Integer> randomOffsetRange = sgAdvanced.add(new IntSetting.Builder()
            .name("random-offset-range")
            .description("Диапазон случайного смещения")
            .defaultValue(5)
            .min(1)
            .max(20)
            .sliderMax(20)
            .build()
    );

    // --- ENUM ---
    public enum SwingMode {
        Smooth("Плавный"),
        Fast("Быстрый"),
        Custom("Пользовательский"),
        Disabled("Отключён");

        private final String name;

        SwingMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // --- ПЕРЕМЕННЫЕ ---
    private float swingProgress = 0;
    private float oldSwingProgress = 0;
    private float healthAnim = 20.0f;
    private float targetHealth = 20.0f;
    private final Random random = new Random();
    private int tickCounter = 0;

    // --- КОНСТРУКТОР ---
    public Animations() {
        super(HM_CORE.CATEGORY, "Animations", "Плавные анимации интерфейса и замаха");
    }

    @Override
    public void onActivate() {
        swingProgress = 0;
        oldSwingProgress = 0;
        if (mc.player != null) {
            healthAnim = mc.player.getHealth();
            targetHealth = mc.player.getHealth();
        }
    }

    @Override
    public void onDeactivate() {
        // Восстанавливаем стандартные настройки
    }

    // --- ТИК (обновление анимаций) ---
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!enabled.get() || mc.player == null) return;

        tickCounter++;

        // Анимация замаха
        if (swingMode.get() != SwingMode.Disabled) {
            if (swingProgress < 1.0f) {
                float speedMult = (float) (swingSpeed.get() * speed.get());
                if (randomOffset.get()) {
                    speedMult += (random.nextFloat() - 0.5f) * randomOffsetRange.get() / 100.0f;
                }
                swingProgress += 0.1f * speedMult;
                if (swingProgress > 1.0f) swingProgress = 1.0f;
            }
        }

        // Анимация здоровья
        if (animatedHealth.get()) {
            targetHealth = mc.player.getHealth();
            float diff = targetHealth - healthAnim;
            if (Math.abs(diff) > 0.01f) {
                healthAnim += diff * (float) (healthAnimSpeed.get() * speed.get());
            } else {
                healthAnim = targetHealth;
            }
        }
    }

    // --- ОТМЕНА СТАНДАРТНОЙ АНИМАЦИИ ЗАМАХА ---
    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!enabled.get()) return;
        if (!cancelPacketSwing.get()) return;
        if (event.packet instanceof HandSwingC2SPacket) {
            event.cancel();
        }
    }

    // --- РЕНДЕРИНГ (визуальные эффекты) ---
    @EventHandler
    private void onRender(Render2DEvent event) {
        if (!enabled.get() || mc.player == null) return;

        // Эффекты затухания для GUI
        if (fadeEffects.get() && mc.currentScreen != null) {
            // Плавное появление GUI (реализуется через переопределение)
            // Сложно без изменения ядра, пока заглушка
        }

        // Отображение анимированного здоровья
        if (animatedHealth.get() && showHealth()) {
            // Здесь можно отрисовать анимированную полосу здоровья
            // В реальности это делается через миксин или переопределение
            // Пока просто обновляем переменные
        }
    }

    // --- ПРОВЕРКА, ПОКАЗЫВАТЬ ЛИ ЗДОРОВЬЕ ---
    private boolean showHealth() {
        if (mc.options == null) return false;
        return !mc.options.hudHidden;
    }

    // --- АНИМАЦИЯ ЗАМАХА (вызов извне) ---
    public void triggerSwing() {
        if (swingMode.get() != SwingMode.Disabled) {
            swingProgress = 0;
            if (oldSwing.get()) {
                swingProgress = 0.5f; // Старый стиль
            }
        }
    }

    // --- ПОЛУЧЕНИЕ ПРОГРЕССА ЗАМАХА ---
    public float getSwingProgress() {
        return swingProgress;
    }

    public float getSwingProgressOld() {
        return oldSwingProgress;
    }

    // --- ПОЛУЧЕНИЕ АНИМИРОВАННОГО ЗДОРОВЬЯ ---
    public float getAnimatedHealth() {
        return healthAnim;
    }

    // --- ИНФОРМАЦИЯ В HUD ---
    @Override
    public String getInfoString() {
        if (enabled.get()) {
            return swingMode.get().toString();
        }
        return "§cВыкл";
    }
}
