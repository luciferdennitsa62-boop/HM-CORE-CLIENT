package hmcore.modules.visuals;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import hmcore.HM_CORE;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TrailEffect extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColor = settings.createGroup("Color");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Режим следа")
            .defaultValue(Mode.Fire)
            .build()
    );

    public final Setting<Integer> length = sgGeneral.add(new IntSetting.Builder()
            .name("length")
            .description("Длина следа (количество точек)")
            .defaultValue(20)
            .min(5)
            .max(100)
            .sliderMax(100)
            .build()
    );

    public final Setting<Double> particleSize = sgGeneral.add(new DoubleSetting.Builder()
            .name("particle-size")
            .description("Размер частиц")
            .defaultValue(0.5)
            .min(0.1)
            .max(2.0)
            .sliderMax(2.0)
            .build()
    );

    public final Setting<Integer> transparency = sgGeneral.add(new IntSetting.Builder()
            .name("transparency")
            .description("Прозрачность (0–255)")
            .defaultValue(200)
            .min(0)
            .max(255)
            .sliderMax(255)
            .build()
    );

    public final Setting<Integer> particleCount = sgGeneral.add(new IntSetting.Builder()
            .name("particle-count")
            .description("Количество частиц на точку")
            .defaultValue(2)
            .min(1)
            .max(10)
            .sliderMax(10)
            .build()
    );

    public final Setting<Boolean> onlyWhenMoving = sgGeneral.add(new BoolSetting.Builder()
            .name("only-when-moving")
            .description("След только при движении")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> sound = sgGeneral.add(new BoolSetting.Builder()
            .name("sound")
            .description("Воспроизводить звук шипения")
            .defaultValue(false)
            .build()
    );

    // --- НАСТРОЙКИ ЦВЕТА ---

    public final Setting<ColorMode> colorMode = sgColor.add(new EnumSetting.Builder<ColorMode>()
            .name("color-mode")
            .description("Режим цвета")
            .defaultValue(ColorMode.Solid)
            .build()
    );

    public final Setting<List<Color>> colorList = sgColor.add(new ColorListSetting.Builder()
            .name("color-list")
            .description("Цвета для градиента или радуги")
            .defaultValue(new Color(0xFF0000), new Color(0xFF6600), new Color(0xFFFF00))
            .build()
    );

    public final Setting<Color> solidColor = sgColor.add(new ColorSetting.Builder()
            .name("solid-color")
            .description("Цвет для сплошного режима")
            .defaultValue(new Color(0xFF0000))
            .build()
    );

    public final Setting<Integer> hueSpeed = sgColor.add(new IntSetting.Builder()
            .name("hue-speed")
            .description("Скорость смены цветов (для радуги)")
            .defaultValue(5)
            .min(1)
            .max(20)
            .sliderMax(20)
            .build()
    );

    // --- ПРОДВИНУТЫЕ НАСТРОЙКИ ---

    public final Setting<Boolean> glow = sgAdvanced.add(new BoolSetting.Builder()
            .name("glow")
            .description("Эффект свечения (только для DustParticle)")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> randomOffset = sgAdvanced.add(new BoolSetting.Builder()
            .name("random-offset")
            .description("Случайное смещение частиц")
            .defaultValue(true)
            .build()
    );

    public final Setting<Double> offsetAmount = sgAdvanced.add(new DoubleSetting.Builder()
            .name("offset-amount")
            .description("Величина смещения")
            .defaultValue(0.2)
            .min(0.0)
            .max(1.0)
            .sliderMax(1.0)
            .build()
    );

    // --- ENUM'Ы ---

    public enum Mode {
        Fire("Огненный"),
        Smoke("Дымный"),
        Gradient("Градиент"),
        Rainbow("Радуга"),
        Custom("Пользовательский");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum ColorMode {
        Solid("Сплошной"),
        Gradient("Градиент"),
        Rainbow("Радуга");

        private final String name;

        ColorMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // --- ПЕРЕМЕННЫЕ ---

    private final List<Vec3d> positions = new ArrayList<>();
    private int hue = 0;
    private int tickCounter = 0;

    // --- КОНСТРУКТОР ---

    public TrailEffect() {
        super(HM_CORE.CATEGORY, "TrailEffect", "Огненный/дымный след за игроком");
    }

    @Override
    public void onActivate() {
        positions.clear();
        hue = 0;
        tickCounter = 0;
        if (sound.get()) {
            ChatUtils.info("TrailEffect включён. Шипим!");
        }
    }

    @Override
    public void onDeactivate() {
        positions.clear();
        if (sound.get()) {
            ChatUtils.info("TrailEffect выключен.");
        }
    }

    // --- ОСНОВНОЙ ТИК ---

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Проверка движения
        if (onlyWhenMoving.get() && mc.player.getVelocity().length() < 0.01) {
            // Если стоим — не добавляем позиции, но не очищаем след
            return;
        }

        // Добавляем текущую позицию
        positions.add(mc.player.getPos());

        // Ограничиваем длину
        while (positions.size() > length.get()) {
            positions.remove(0);
        }

        // Воспроизведение звука
        if (sound.get() && tickCounter % 10 == 0) {
            // Здесь можно проиграть звук через mc.player.playSound()
            // Например, mc.player.playSound(SoundEvents.BLOCK_FIRE_AMBIENT, 0.5f, 1.0f);
        }

        // Рендерим частицы
        renderParticles();

        tickCounter++;
    }

    // --- РЕНДЕРИНГ ЧАСТИЦ ---

    private void renderParticles() {
        if (mc.world == null) return;

        for (int i = 0; i < positions.size(); i++) {
            Vec3d pos = positions.get(i);
            Color color = getColor(i);

            // Цвет в формате RGB
            float r = color.getRed() / 255f;
            float g = color.getGreen() / 255f;
            float b = color.getBlue() / 255f;
            float a = transparency.get() / 255f;

            // Количество частиц на точку
            for (int j = 0; j < particleCount.get(); j++) {
                double xOff = 0;
                double yOff = 0;
                double zOff = 0;

                if (randomOffset.get()) {
                    double offset = offsetAmount.get();
                    xOff = (Math.random() - 0.5) * offset;
                    yOff = (Math.random() - 0.5) * offset;
                    zOff = (Math.random() - 0.5) * offset;
                }

                // Выбор типа частиц в зависимости от режима
                switch (mode.get()) {
                    case Fire -> {
                        // Огненные частицы
                        mc.world.addParticle(ParticleTypes.FLAME,
                                pos.x + xOff, pos.y + yOff, pos.z + zOff,
                                0, 0.05, 0);
                    }
                    case Smoke -> {
                        // Дымные частицы
                        mc.world.addParticle(ParticleTypes.SMOKE,
                                pos.x + xOff, pos.y + yOff, pos.z + zOff,
                                0, 0.02, 0);
                    }
                    case Gradient, Rainbow, Custom -> {
                        // DustParticle для цветных следов
                        DustParticleEffect dust = new DustParticleEffect(
                                r, g, b, (float) particleSize.get()
                        );
                        mc.world.addParticle(dust,
                                pos.x + xOff, pos.y + yOff, pos.z + zOff,
                                0, 0, 0);
                    }
                }
            }
        }
    }

    // --- ОПРЕДЕЛЕНИЕ ЦВЕТА ДЛЯ ТОЧКИ ---

    private Color getColor(int index) {
        switch (colorMode.get()) {
            case Solid:
                return solidColor.get();

            case Gradient:
                if (colorList.get().isEmpty()) return Color.RED;
                float progress = (float) index / length.get();
                return interpolateColors(colorList.get(), progress);

            case Rainbow:
                hue = (hue + hueSpeed.get()) % 360;
                return Color.getHSBColor((float) hue / 360f, 1.0f, 1.0f);

            default:
                return Color.RED;
        }
    }

    // --- ИНТЕРПОЛЯЦИЯ ДЛЯ ГРАДИЕНТА ---

    private Color interpolateColors(List<Color> colors, float progress) {
        if (colors.size() == 1) return colors.get(0);
        if (progress >= 1.0f) return colors.get(colors.size() - 1);

        float segment = 1.0f / (colors.size() - 1);
        int idx = (int) (progress / segment);
        if (idx >= colors.size() - 1) return colors.get(colors.size() - 1);

        float localProgress = (progress - idx * segment) / segment;
        Color c1 = colors.get(idx);
        Color c2 = colors.get(idx + 1);

        int r = (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * localProgress);
        int g = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * localProgress);
        int b = (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * localProgress);

        return new Color(r, g, b);
    }

    // --- ДЛЯ ОТОБРАЖЕНИЯ В HUD (опционально) ---

    @EventHandler
    private void onTickRender(TickEvent.Post event) {
        // Здесь можно отобразить информацию о длине следа
    }
}
