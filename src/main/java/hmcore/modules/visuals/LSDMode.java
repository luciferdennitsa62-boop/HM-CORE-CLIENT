package hmcore.modules.visuals;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.util.Window;
import hmcore.HM_CORE;

import java.awt.*;
import java.util.Random;

public class LSDMode extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
            .name("speed")
            .description("Скорость смены цветов")
            .defaultValue(1.0)
            .min(0.1)
            .max(5.0)
            .sliderMax(5.0)
            .build()
    );

    public final Setting<Double> intensity = sgGeneral.add(new DoubleSetting.Builder()
            .name("intensity")
            .description("Интенсивность эффекта (насколько сильно меняются цвета)")
            .defaultValue(1.0)
            .min(0.1)
            .max(2.0)
            .sliderMax(2.0)
            .build()
    );

    public final Setting<EffectType> effect = sgGeneral.add(new EnumSetting.Builder<EffectType>()
            .name("effect")
            .description("Тип эффекта")
            .defaultValue(EffectType.Rainbow)
            .build()
    );

    public final Setting<Integer> waveLength = sgGeneral.add(new IntSetting.Builder()
            .name("wave-length")
            .description("Длина волны (для Wave эффекта)")
            .defaultValue(10)
            .min(2)
            .max(50)
            .sliderMax(50)
            .build()
    );

    public final Setting<Double> distortion = sgGeneral.add(new DoubleSetting.Builder()
            .name("distortion")
            .description("Искажение (для эффекта искажения)")
            .defaultValue(0.1)
            .min(0.0)
            .max(1.0)
            .sliderMax(1.0)
            .build()
    );

    // --- ЦВЕТА ---

    public final Setting<Color> color1 = sgColors.add(new ColorSetting.Builder()
            .name("color1")
            .description("Первый цвет (для градиента)")
            .defaultValue(new Color(0xFF0000))
            .build()
    );

    public final Setting<Color> color2 = sgColors.add(new ColorSetting.Builder()
            .name("color2")
            .description("Второй цвет (для градиента)")
            .defaultValue(new Color(0x00FF00))
            .build()
    );

    public final Setting<Color> color3 = sgColors.add(new ColorSetting.Builder()
            .name("color3")
            .description("Третий цвет (для градиента)")
            .defaultValue(new Color(0x0000FF))
            .build()
    );

    // --- ПРОДВИНУТЫЕ ---

    public final Setting<Boolean> affectHUD = sgAdvanced.add(new BoolSetting.Builder()
            .name("affect-hud")
            .description("Влиять на HUD")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> affectWorld = sgAdvanced.add(new BoolSetting.Builder()
            .name("affect-world")
            .description("Влиять на мир")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> randomColors = sgAdvanced.add(new BoolSetting.Builder()
            .name("random-colors")
            .description("Случайные цвета")
            .defaultValue(false)
            .build()
    );

    // --- ENUM ---
    public enum EffectType {
        Rainbow("Радуга"),
        Pulse("Пульсация"),
        Wave("Волна"),
        Distortion("Искажение"),
        Gradient("Градиент"),
        Random("Случайный");

        private final String name;

        EffectType(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // --- ПЕРЕМЕННЫЕ ---
    private float hue = 0;
    private int tickCounter = 0;
    private final Random random = new Random();

    // --- КОНСТРУКТОР ---
    public LSDMode() {
        super(HM_CORE.CATEGORY, "LSDMode", "Психоделический режим для стримов (меняет цвета экрана)");
    }

    @Override
    public void onActivate() {
        hue = 0;
        tickCounter = 0;
    }

    @Override
    public void onDeactivate() {
        // Возвращаем нормальные цвета
        if (mc.world != null) {
            // Сброс гаммы и цветов
        }
    }

    // --- ТИК ---
    @EventHandler
    private void onTick(TickEvent.Post event) {
        tickCounter++;
        float step = (float) (speed.get() * 0.01f);
        hue = (hue + step) % 1.0f;

        // Случайные цвета
        if (randomColors.get() && tickCounter % 20 == 0) {
            color1.set(new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
            color2.set(new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
            color3.set(new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
        }
    }

    // --- РЕНДЕРИНГ (наложение эффекта) ---
    @EventHandler
    private void onRender(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Window window = mc.getWindow();
        int width = window.getWidth();
        int height = window.getHeight();

        // Здесь должен быть код наложения шейдера или фильтра
        // В стандартном Minecraft это сложно без шейдеров
        // Поэтому мы используем упрощённый вариант: рисуем поверх экрана

        // Для реального эффекта нужны шейдеры, но пока заглушка
        // В будущем можно интегрировать через пост-обработку

        // Простой эффект: рисуем полупрозрачный прямоугольник с меняющимся цветом
        if (effect.get() == EffectType.Rainbow) {
            Color c = Color.fromHSB(hue, 1.0f, 1.0f);
            event.drawQuad(0, 0, width, height, new Color(c.r, c.g, c.b, 20));
        } else if (effect.get() == EffectType.Pulse) {
            float pulse = (float) (0.3 + 0.7 * Math.abs(Math.sin(tickCounter * speed.get() * 0.05)));
            Color c = new Color(255, (int)(255 * pulse), (int)(255 * (1 - pulse)), 30);
            event.drawQuad(0, 0, width, height, c);
        } else if (effect.get() == EffectType.Wave) {
            // Волны
            for (int i = 0; i < waveLength.get(); i++) {
                float progress = (float) i / waveLength.get();
                float alpha = (float) (0.1 * Math.sin(tickCounter * speed.get() * 0.1 + i * 0.5) + 0.1);
                Color c = new Color(
                        (int)(255 * (0.5 + 0.5 * Math.sin(progress * Math.PI * 2))),
                        (int)(255 * (0.5 + 0.5 * Math.cos(progress * Math.PI * 2))),
                        (int)(255 * (0.5 + 0.5 * Math.sin(progress * Math.PI * 2 + 2))),
                        (int)(alpha * 255)
                );
                int y = height / waveLength.get() * i;
                event.drawQuad(0, y, width, height / waveLength.get(), c);
            }
        } else if (effect.get() == EffectType.Gradient) {
            // Градиент от color1 к color2
            Color c1 = color1.get();
            Color c2 = color2.get();
            Color c3 = color3.get();

            float progress = (float) (0.5 + 0.5 * Math.sin(tickCounter * speed.get() * 0.05));
            int r = (int) (c1.r + (c2.r - c1.r) * progress);
            int g = (int) (c1.g + (c2.g - c1.g) * progress);
            int b = (int) (c1.b + (c2.b - c1.b) * progress);

            Color c = new Color(r, g, b, 30);
            event.drawQuad(0, 0, width, height, c);
        } else if (effect.get() == EffectType.Random) {
            int r = random.nextInt(256);
            int g = random.nextInt(256);
            int b = random.nextInt(256);
            Color c = new Color(r, g, b, 20);
            event.drawQuad(0, 0, width, height, c);
        } else if (effect.get() == EffectType.Distortion) {
            // Искажение — упрощённая версия
            double d = distortion.get() * 10;
            for (int i = 0; i < 50; i++) {
                int xOffset = (int) (Math.sin(tickCounter * speed.get() * 0.1 + i * 0.5) * d);
                int yOffset = (int) (Math.cos(tickCounter * speed.get() * 0.1 + i * 0.5) * d);
                Color c = new Color(255, 255, 255, 10);
                event.drawQuad(xOffset + i * 20, yOffset + i * 10, 5, 5, c);
            }
        }
    }

    @Override
    public String getInfoString() {
        return effect.get().toString() + " " + String.format("%.1f", speed.get());
    }
}
