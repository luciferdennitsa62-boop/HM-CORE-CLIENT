package hmcore.modules.visuals;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorWidget;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import hmcore.HM_CORE;

import java.awt.*;

public class RedBlackTheme extends Module {

    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgBackground = settings.createGroup("Background");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // --- ОСНОВНЫЕ ЦВЕТА ---

    public final Setting<Color> primaryColor = sgColors.add(new ColorSetting.Builder()
            .name("primary-color")
            .description("Основной цвет (красный)")
            .defaultValue(new Color(0xCC0000))
            .build()
    );

    public final Setting<Color> secondaryColor = sgColors.add(new ColorSetting.Builder()
            .name("secondary-color")
            .description("Вторичный цвет (тёмно-серый)")
            .defaultValue(new Color(0x1A1A1A))
            .build()
    );

    public final Setting<Color> accentColor = sgColors.add(new ColorSetting.Builder()
            .name("accent-color")
            .description("Цвет акцентов (ярко-красный)")
            .defaultValue(new Color(0xFF3333))
            .build()
    );

    public final Setting<Color> textColor = sgColors.add(new ColorSetting.Builder()
            .name("text-color")
            .description("Цвет текста")
            .defaultValue(new Color(0xFFFFFF))
            .build()
    );

    public final Setting<Color> textSecondaryColor = sgColors.add(new ColorSetting.Builder()
            .name("text-secondary-color")
            .description("Цвет второстепенного текста")
            .defaultValue(new Color(0x888888))
            .build()
    );

    // --- ФОН ---

    public final Setting<Color> backgroundColor = sgBackground.add(new ColorSetting.Builder()
            .name("background-color")
            .description("Цвет фона")
            .defaultValue(new Color(0x0D0D0D))
            .build()
    );

    public final Setting<Color> panelColor = sgBackground.add(new ColorSetting.Builder()
            .name("panel-color")
            .description("Цвет панелей")
            .defaultValue(new Color(0x1A1A1A))
            .build()
    );

    public final Setting<Color> borderColor = sgBackground.add(new ColorSetting.Builder()
            .name("border-color")
            .description("Цвет рамок")
            .defaultValue(new Color(0x333333))
            .build()
    );

    public final Setting<Integer> blurAmount = sgBackground.add(new IntSetting.Builder()
            .name("blur-amount")
            .description("Степень размытия фона (0–20)")
            .defaultValue(5)
            .min(0)
            .max(20)
            .sliderMax(20)
            .build()
    );

    // --- ПРОДВИНУТЫЕ НАСТРОЙКИ ---

    public final Setting<Boolean> useGradient = sgAdvanced.add(new BoolSetting.Builder()
            .name("use-gradient")
            .description("Использовать градиент вместо сплошных цветов")
            .defaultValue(false)
            .build()
    );

    public final Setting<Double> gradientSpeed = sgAdvanced.add(new DoubleSetting.Builder()
            .name("gradient-speed")
            .description("Скорость анимации градиента")
            .defaultValue(1.0)
            .min(0.1)
            .max(5.0)
            .sliderMax(5.0)
            .build()
    );

    public final Setting<Boolean> roundedCorners = sgAdvanced.add(new BoolSetting.Builder()
            .name("rounded-corners")
            .description("Скруглённые углы")
            .defaultValue(true)
            .build()
    );

    public final Setting<Integer> cornerRadius = sgAdvanced.add(new IntSetting.Builder()
            .name("corner-radius")
            .description("Радиус скругления углов")
            .defaultValue(4)
            .min(0)
            .max(20)
            .sliderMax(20)
            .build()
    );

    // --- КОНСТРУКТОР ---

    public RedBlackTheme() {
        super(HM_CORE.CATEGORY, "RedBlackTheme", "Красно-чёрно-серая тема оформления");
    }

    @Override
    public void onActivate() {
        applyTheme();
    }

    @Override
    public void onDeactivate() {
        // Возвращаем стандартные цвета Meteor
        resetTheme();
    }

    // --- ПРИМЕНЕНИЕ ТЕМЫ ---

    private void applyTheme() {
        // Здесь мы переопределяем цвета в GuiTheme
        // В Meteor это делается через переопределение GuiTheme
        // Для простоты мы сохраняем настройки в переменные
        // и в дальнейшем используем их в рендеринге

        // Реальная реализация требует переопределения темы в GuiThemes
        // Но для совместимости мы используем стандартный подход через настройки

        // Устанавливаем цвета в конфиг для использования в HUD и GUI
        // В этом примере мы просто сохраняем их в статические переменные

        // Для полной интеграции нужно переопределить метеор-тему
        // Пока оставляем как заглушку с возможностью расширения
    }

    private void resetTheme() {
        // Возврат стандартных цветов
    }

    // --- ПОЛУЧЕНИЕ ЦВЕТОВ ДЛЯ ДРУГИХ МОДУЛЕЙ ---

    public Color getPrimary() {
        return primaryColor.get();
    }

    public Color getSecondary() {
        return secondaryColor.get();
    }

    public Color getAccent() {
        return accentColor.get();
    }

    public Color getText() {
        return textColor.get();
    }

    public Color getTextSecondary() {
        return textSecondaryColor.get();
    }

    public Color getBackground() {
        return backgroundColor.get();
    }

    public Color getPanel() {
        return panelColor.get();
    }

    public Color getBorder() {
        return borderColor.get();
    }

    // --- ОТОБРАЖЕНИЕ В HUD ---

    @Override
    public String getInfoString() {
        return "Цветов: " + primaryColor.get().toString();
    }
}
