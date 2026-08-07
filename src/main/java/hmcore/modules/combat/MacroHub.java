package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Pair;
import org.lwjgl.glfw.GLFW;
import hmcore.HM_CORE;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class MacroHub extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRecord = settings.createGroup("Record");
    private final SettingGroup sgPlay = settings.createGroup("Play");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Режим работы макроса")
            .defaultValue(Mode.Off)
            .build()
    );

    public final Setting<String> macroName = sgGeneral.add(new StringSetting.Builder()
            .name("macro-name")
            .description("Имя макроса (для сохранения/загрузки)")
            .defaultValue("my_macro")
            .build()
    );

    public final Setting<Boolean> loop = sgGeneral.add(new BoolSetting.Builder()
            .name("loop")
            .description("Зациклить макрос")
            .defaultValue(false)
            .build()
    );

    public final Setting<Integer> loopDelay = sgGeneral.add(new IntSetting.Builder()
            .name("loop-delay")
            .description("Задержка между циклами (мс)")
            .defaultValue(100)
            .min(0)
            .max(5000)
            .sliderMax(5000)
            .build()
    );

    public final Setting<Boolean> saveToFile = sgGeneral.add(new BoolSetting.Builder()
            .name("save-to-file")
            .description("Сохранять макрос в файл")
            .defaultValue(true)
            .build()
    );

    // --- НАСТРОЙКИ ЗАПИСИ ---

    public final Setting<Boolean> recordMouse = sgRecord.add(new BoolSetting.Builder()
            .name("record-mouse")
            .description("Записывать движение мыши")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> recordClicks = sgRecord.add(new BoolSetting.Builder()
            .name("record-clicks")
            .description("Записывать клики мыши")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> recordKeyboard = sgRecord.add(new BoolSetting.Builder()
            .name("record-keyboard")
            .description("Записывать нажатия клавиш")
            .defaultValue(true)
            .build()
    );

    public final Setting<Integer> maxActions = sgRecord.add(new IntSetting.Builder()
            .name("max-actions")
            .description("Максимальное количество действий в макросе")
            .defaultValue(10000)
            .min(100)
            .max(100000)
            .sliderMax(100000)
            .build()
    );

    // --- НАСТРОЙКИ ВОСПРОИЗВЕДЕНИЯ ---

    public final Setting<Double> playbackSpeed = sgPlay.add(new DoubleSetting.Builder()
            .name("playback-speed")
            .description("Скорость воспроизведения (1.0 = норма)")
            .defaultValue(1.0)
            .min(0.1)
            .max(5.0)
            .sliderMax(5.0)
            .build()
    );

    public final Setting<Boolean> skipErrors = sgPlay.add(new BoolSetting.Builder()
            .name("skip-errors")
            .description("Пропускать ошибки при воспроизведении")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> randomizeDelay = sgPlay.add(new BoolSetting.Builder()
            .name("randomize-delay")
            .description("Случайная задержка между действиями (для обхода античитов)")
            .defaultValue(false)
            .build()
    );

    public final Setting<Integer> randomDelayRange = sgPlay.add(new IntSetting.Builder()
            .name("random-delay-range")
            .description("Диапазон случайной задержки (мс)")
            .defaultValue(50)
            .min(0)
            .max(500)
            .sliderMax(500)
            .build()
    );

    // --- ПРОДВИНУТЫЕ ---

    public final Setting<Boolean> priorityMode = sgAdvanced.add(new BoolSetting.Builder()
            .name("priority-mode")
            .description("Приоритетный режим (не пропускать тики)")
            .defaultValue(false)
            .build()
    );

    public final Setting<Integer> priorityTicks = sgAdvanced.add(new IntSetting.Builder()
            .name("priority-ticks")
            .description("Количество тиков для приоритета")
            .defaultValue(20)
            .min(1)
            .max(100)
            .sliderMax(100)
            .build()
    );

    // --- ENUM ---
    public enum Mode {
        Off("Выкл"),
        Record("Запись"),
        Play("Воспроизведение");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // --- КЛАСС ДЛЯ ДЕЙСТВИЙ ---
    private static class MacroAction {
        public final long timestamp;
        public final ActionType type;
        public final int keyCode;
        public final int mouseButton;
        public final double mouseX;
        public final double mouseY;
        public final boolean pressed;

        public MacroAction(long timestamp, ActionType type, int keyCode, int mouseButton, double mouseX, double mouseY, boolean pressed) {
            this.timestamp = timestamp;
            this.type = type;
            this.keyCode = keyCode;
            this.mouseButton = mouseButton;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.pressed = pressed;
        }

        @Override
        public String toString() {
            return String.format("%d|%s|%d|%d|%.2f|%.2f|%b", timestamp, type.name(), keyCode, mouseButton, mouseX, mouseY, pressed);
        }

        public static MacroAction fromString(String str) {
            String[] parts = str.split("\\|");
            if (parts.length < 7) return null;
            long ts = Long.parseLong(parts[0]);
            ActionType type = ActionType.valueOf(parts[1]);
            int key = Integer.parseInt(parts[2]);
            int btn = Integer.parseInt(parts[3]);
            double mx = Double.parseDouble(parts[4]);
            double my = Double.parseDouble(parts[5]);
            boolean press = Boolean.parseBoolean(parts[6]);
            return new MacroAction(ts, type, key, btn, mx, my, press);
        }
    }

    private enum ActionType {
        Key, MouseClick, MouseMove, Scroll
    }

    // --- ПЕРЕМЕННЫЕ ---
    private List<MacroAction> actions = new ArrayList<>();
    private long startTime = 0;
    private int playIndex = 0;
    private long lastPlayTime = 0;
    private boolean isPaused = false;
    private final Random random = new Random();

    // --- КОНСТРУКТОР ---
    public MacroHub() {
        super(HM_CORE.CATEGORY, "MacroHub", "Запись и воспроизведение макросов");
    }

    @Override
    public void onActivate() {
        if (mode.get() == Mode.Record) {
            startRecording();
        } else if (mode.get() == Mode.Play) {
            startPlayback();
        }
    }

    @Override
    public void onDeactivate() {
        if (mode.get() == Mode.Record) {
            stopRecording();
        } else if (mode.get() == Mode.Play) {
            stopPlayback();
        }
        mode.set(Mode.Off);
    }

    // --- ЗАПИСЬ ---
    private void startRecording() {
        actions.clear();
        startTime = System.currentTimeMillis();
        ChatUtils.info("§aЗапись макроса начата...");
        if (saveToFile.get()) {
            ChatUtils.info("§7Имя файла: " + macroName.get() + ".macro");
        }
        // Регистрируем обработчики клавиш и мыши
        // В реальности это делается через миксины или события
    }

    private void stopRecording() {
        if (saveToFile.get() && !actions.isEmpty()) {
            saveMacro();
        }
        ChatUtils.info("§aЗапись завершена. Записано действий: " + actions.size());
        if (mode.get() == Mode.Record) {
            mode.set(Mode.Off);
        }
    }

    // --- ВОСПРОИЗВЕДЕНИЕ ---
    private void startPlayback() {
        if (saveToFile.get()) {
            loadMacro();
        }
        if (actions.isEmpty()) {
            ChatUtils.warn("Макрос пуст! Загрузите или запишите макрос.");
            mode.set(Mode.Off);
            toggle();
            return;
        }
        playIndex = 0;
        startTime = System.currentTimeMillis();
        lastPlayTime = System.currentTimeMillis();
        ChatUtils.info("§aВоспроизведение макроса начато. Действий: " + actions.size());
    }

    private void stopPlayback() {
        ChatUtils.info("§aВоспроизведение завершено.");
        if (mode.get() == Mode.Play) {
            mode.set(Mode.Off);
        }
    }

    // --- ТИК (основной цикл) ---
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (mode.get() == Mode.Record) {
            // Запись действий
            if (System.currentTimeMillis() - startTime > 60000) {
                ChatUtils.warn("Достигнут лимит времени (60 сек), запись остановлена");
                mode.set(Mode.Off);
                toggle();
                return;
            }
            // Здесь будет захват ввода
        }

        if (mode.get() == Mode.Play) {
            // Воспроизведение
            if (playIndex >= actions.size()) {
                if (loop.get()) {
                    // Зацикливание
                    playIndex = 0;
                    startTime = System.currentTimeMillis();
                    try {
                        Thread.sleep(loopDelay.get());
                    } catch (InterruptedException ignored) {}
                    return;
                } else {
                    stopPlayback();
                    toggle();
                    return;
                }
            }

            MacroAction action = actions.get(playIndex);
            long now = System.currentTimeMillis();
            long elapsed = now - startTime;
            long targetTime = (long) (action.timestamp * playbackSpeed.get());

            if (randomizeDelay.get()) {
                targetTime += random.nextInt(randomDelayRange.get() + 1);
            }

            if (elapsed >= targetTime) {
                executeAction(action);
                playIndex++;
                lastPlayTime = now;
            }
        }
    }

    // --- ВЫПОЛНЕНИЕ ДЕЙСТВИЯ ---
    private void executeAction(MacroAction action) {
        if (mc.player == null) return;

        switch (action.type) {
            case Key:
                KeyBinding keyBinding = getKeyBinding(action.keyCode);
                if (keyBinding != null) {
                    keyBinding.setPressed(action.pressed);
                }
                break;
            case MouseClick:
                if (action.mouseButton == 0) {
                    if (action.pressed) {
                        mc.options.attackKey.setPressed(true);
                    } else {
                        mc.options.attackKey.setPressed(false);
                    }
                } else if (action.mouseButton == 1) {
                    if (action.pressed) {
                        mc.options.useKey.setPressed(true);
                    } else {
                        mc.options.useKey.setPressed(false);
                    }
                }
                break;
            case MouseMove:
                if (mc.player != null) {
                    // Движение мыши (сложно, можно пропустить)
                }
                break;
            default:
                break;
        }
    }

    // --- ПОИСК КЛАВИШИ ПО КОДУ ---
    private KeyBinding getKeyBinding(int keyCode) {
        for (KeyBinding kb : KeyBinding.stream().toArray(KeyBinding[]::new)) {
            if (kb.getDefaultKey().getCode() == keyCode || kb.getBoundKey().getCode() == keyCode) {
                return kb;
            }
        }
        return null;
    }

    // --- СОХРАНЕНИЕ МАКРОСА В ФАЙЛ ---
    private void saveMacro() {
        try {
            Path path = Paths.get("hmcore_macros", macroName.get() + ".macro");
            Files.createDirectories(path.getParent());
            BufferedWriter writer = Files.newBufferedWriter(path);
            for (MacroAction action : actions) {
                writer.write(action.toString());
                writer.newLine();
            }
            writer.close();
            ChatUtils.info("§aМакрос сохранён: " + path.toString());
        } catch (IOException e) {
            ChatUtils.error("Ошибка сохранения макроса: " + e.getMessage());
        }
    }

    // --- ЗАГРУЗКА МАКРОСА ИЗ ФАЙЛА ---
    private void loadMacro() {
        try {
            Path path = Paths.get("hmcore_macros", macroName.get() + ".macro");
            if (!Files.exists(path)) {
                ChatUtils.warn("Файл макроса не найден: " + path.toString());
                return;
            }
            List<String> lines = Files.readAllLines(path);
            actions.clear();
            for (String line : lines) {
                MacroAction action = MacroAction.fromString(line);
                if (action != null) {
                    actions.add(action);
                }
            }
            ChatUtils.info("§aМакрос загружен: " + actions.size() + " действий");
        } catch (IOException e) {
            ChatUtils.error("Ошибка загрузки макроса: " + e.getMessage());
        }
    }

    // --- ИНФОРМАЦИЯ В HUD ---
    @Override
    public String getInfoString() {
        if (mode.get() == Mode.Record) {
            return "§aЗапись... " + actions.size();
        } else if (mode.get() == Mode.Play) {
            return "§aВоспр. " + playIndex + "/" + actions.size();
        }
        return "§cВыкл";
    }
}
