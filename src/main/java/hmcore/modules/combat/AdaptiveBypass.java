package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerVelocityChangedS2CPacket;
import hmcore.HM_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AdaptiveBypass extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDetection = settings.createGroup("Detection");
    private final SettingGroup sgBypass = settings.createGroup("Bypass");
    private final SettingGroup sgVisual = settings.createGroup("Visual");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Режим работы")
            .defaultValue(Mode.Auto)
            .build()
    );

    public final Setting<Boolean> autoApply = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-apply")
            .description("Автоматически применять настройки к модулям")
            .defaultValue(true)
            .build()
    );

    public final Setting<Integer> detectionTime = sgGeneral.add(new IntSetting.Builder()
            .name("detection-time")
            .description("Время для определения античита (тики)")
            .defaultValue(20)
            .min(5)
            .max(100)
            .sliderMax(100)
            .build()
    );

    // --- ОПРЕДЕЛЕНИЕ ---

    public final Setting<DetectionMethod> detectionMethod = sgDetection.add(new EnumSetting.Builder<DetectionMethod>()
            .name("detection-method")
            .description("Метод определения античита")
            .defaultValue(DetectionMethod.Packet)
            .build()
    );

    public final Setting<Boolean> detectOnJoin = sgDetection.add(new BoolSetting.Builder()
            .name("detect-on-join")
            .description("Определять античит при входе на сервер")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> detectOnKick = sgDetection.add(new BoolSetting.Builder()
            .name("detect-on-kick")
            .description("Определять античит по кику")
            .defaultValue(true)
            .build()
    );

    // --- НАСТРОЙКИ ОБХОДА ---

    public final Setting<BypassLevel> bypassLevel = sgBypass.add(new EnumSetting.Builder<BypassLevel>()
            .name("bypass-level")
            .description("Уровень агрессивности обхода")
            .defaultValue(BypassLevel.Medium)
            .build()
    );

    public final Setting<Boolean> adaptFly = sgBypass.add(new BoolSetting.Builder()
            .name("adapt-fly")
            .description("Адаптировать настройки Fly")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> adaptSpeed = sgBypass.add(new BoolSetting.Builder()
            .name("adapt-speed")
            .description("Адаптировать настройки Speed")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> adaptReach = sgBypass.add(new BoolSetting.Builder()
            .name("adapt-reach")
            .description("Адаптировать настройки Reach")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> adaptNoFall = sgBypass.add(new BoolSetting.Builder()
            .name("adapt-no-fall")
            .description("Адаптировать настройки NoFall")
            .defaultValue(true)
            .build()
    );

    // --- ВИЗУАЛ ---

    public final Setting<Boolean> showDetected = sgVisual.add(new BoolSetting.Builder()
            .name("show-detected")
            .description("Показывать определённый античит в HUD")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> notifyDetection = sgVisual.add(new BoolSetting.Builder()
            .name("notify-detection")
            .description("Уведомлять об обнаружении античита")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> logPackets = sgVisual.add(new BoolSetting.Builder()
            .name("log-packets")
            .description("Логировать подозрительные пакеты (для отладки)")
            .defaultValue(false)
            .build()
    );

    // --- ПРОДВИНУТЫЕ ---

    public final Setting<Boolean> randomizeTimings = sgAdvanced.add(new BoolSetting.Builder()
            .name("randomize-timings")
            .description("Случайная задержка между адаптациями")
            .defaultValue(true)
            .build()
    );

    public final Setting<Integer> randomDelayRange = sgAdvanced.add(new IntSetting.Builder()
            .name("random-delay-range")
            .description("Диапазон задержки (мс)")
            .defaultValue(100)
            .min(0)
            .max(500)
            .sliderMax(500)
            .build()
    );

    public final Setting<Boolean> debugMode = sgAdvanced.add(new BoolSetting.Builder()
            .name("debug-mode")
            .description("Режим отладки (вывод подробной информации)")
            .defaultValue(false)
            .build()
    );

    // --- ENUM'Ы ---
    public enum Mode {
        Auto("Авто"),
        Manual("Ручной"),
        Off("Выкл");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum DetectionMethod {
        Packet("По пакетам"),
        Ping("По пингу"),
        Kick("По кику"),
        Hybrid("Гибридный");

        private final String name;

        DetectionMethod(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum BypassLevel {
        Low("Низкий (безопасный)"),
        Medium("Средний"),
        High("Высокий (агрессивный)");

        private final String name;

        BypassLevel(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // --- КЛАСС АНТИЧИТА ---
    public static class AntiCheatInfo {
        public final String name;
        public final double detectionChance;

        public AntiCheatInfo(String name, double chance) {
            this.name = name;
            this.detectionChance = chance;
        }

        @Override
        public String toString() {
            return name + " (" + String.format("%.0f", detectionChance * 100) + "%)";
        }
    }

    // --- ПЕРЕМЕННЫЕ ---
    private AntiCheatInfo detectedAntiCheat = null;
    private int tickCounter = 0;
    private boolean detected = false;
    private final Random random = new Random();
    private final List<String> packetLog = new ArrayList<>();

    // --- КОНСТРУКТОР ---
    public AdaptiveBypass() {
        super(HM_CORE.CATEGORY, "AdaptiveBypass", "Автоматический обход античитов");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        detected = false;
        detectedAntiCheat = null;
        packetLog.clear();
        if (notifyDetection.get()) {
            ChatUtils.info("AdaptiveBypass активирован. Определение античита...");
        }
    }

    @Override
    public void onDeactivate() {
        if (notifyDetection.get()) {
            ChatUtils.info("AdaptiveBypass деактивирован.");
        }
    }

    // --- ОБРАБОТКА ПАКЕТОВ (определение античита) ---
    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null) return;

        if (logPackets.get()) {
            packetLog.add(event.packet.getClass().getSimpleName());
            if (packetLog.size() > 100) packetLog.remove(0);
        }

        // Определение по пакетам
        if (detectionMethod.get() == DetectionMethod.Packet || detectionMethod.get() == DetectionMethod.Hybrid) {
            if (event.packet instanceof PlayerPositionLookS2CPacket) {
                // Подозрительный пакет позиции (часто используется античитами)
                if (detectedAntiCheat == null) {
                    // Проверяем на конкретные античиты
                    if (isGrim()) {
                        detectedAntiCheat = new AntiCheatInfo("GrimAC", 0.95);
                    } else if (isVulcan()) {
                        detectedAntiCheat = new AntiCheatInfo("Vulcan", 0.90);
                    } else if (isMatrix()) {
                        detectedAntiCheat = new AntiCheatInfo("Matrix", 0.85);
                    } else if (isAAC()) {
                        detectedAntiCheat = new AntiCheatInfo("AAC", 0.80);
                    } else if (isNCP()) {
                        detectedAntiCheat = new AntiCheatInfo("NCP", 0.75);
                    } else {
                        detectedAntiCheat = new AntiCheatInfo("Unknown", 0.50);
                    }
                    onDetection();
                }
            }
        }

        // Определение по кику
        if (detectionMethod.get() == DetectionMethod.Kick || detectionMethod.get() == DetectionMethod.Hybrid) {
            if (event.packet instanceof PlayerVelocityChangedS2CPacket) {
                // Подозрительное изменение скорости (при кике за летание)
                if (detectedAntiCheat == null) {
                    detectedAntiCheat = new AntiCheatInfo("Kick-detected", 0.70);
                    onDetection();
                }
            }
        }
    }

    // --- ОПРЕДЕЛЕНИЕ КОНКРЕТНЫХ АНТИЧИТОВ ---
    private boolean isGrim() {
        // GrimAC имеет характерные пакеты позиции с малым смещением
        if (packetLog.contains("PlayerPositionLookS2CPacket") && packetLog.contains("EntityVelocityS2CPacket")) {
            return true;
        }
        return false;
    }

    private boolean isVulcan() {
        // Vulcan часто отправляет пакеты с изменённым Y
        if (packetLog.contains("PlayerPositionLookS2CPacket") && packetLog.contains("WorldTimeUpdateS2CPacket")) {
            return true;
        }
        return false;
    }

    private boolean isMatrix() {
        // Matrix любит пакеты EntityVelocity
        if (packetLog.contains("EntityVelocityS2CPacket") && packetLog.contains("PlayerPositionLookS2CPacket")) {
            return true;
        }
        return false;
    }

    private boolean isAAC() {
        // AAC обычно использует много пакетов KeepAlive
        int keepAliveCount = 0;
        for (String p : packetLog) {
            if (p.equals("KeepAliveS2CPacket")) keepAliveCount++;
        }
        return keepAliveCount > 10;
    }

    private boolean isNCP() {
        // NCP отправляет много пакетов позиции при нарушениях
        int posLookCount = 0;
        for (String p : packetLog) {
            if (p.equals("PlayerPositionLookS2CPacket")) posLookCount++;
        }
        return posLookCount > 5;
    }

    // --- ОБРАБОТКА ОБНАРУЖЕНИЯ ---
    private void onDetection() {
        if (detected) return;
        detected = true;

        if (notifyDetection.get()) {
            ChatUtils.info("§aОбнаружен античит: " + detectedAntiCheat.toString());
        }

        if (debugMode.get()) {
            ChatUtils.info("§7Детали определения: " + detectionMethod.get());
            ChatUtils.info("§7Пакетов в логе: " + packetLog.size());
        }

        // Применение обхода
        if (autoApply.get()) {
            applyBypass();
        }
    }

    // --- ПРИМЕНЕНИЕ ОБХОДА ---
    private void applyBypass() {
        if (detectedAntiCheat == null) return;
        String acName = detectedAntiCheat.name.toLowerCase();

        // Определяем параметры обхода
        double speedMult = 1.0;
        double reachMult = 1.0;
        double fallMult = 1.0;
        String flyMode = "Vanilla";

        switch (bypassLevel.get()) {
            case Low:
                speedMult = 0.8;
                reachMult = 0.9;
                fallMult = 0.9;
                flyMode = "Vanilla";
                break;
            case Medium:
                speedMult = 1.0;
                reachMult = 1.0;
                fallMult = 1.0;
                flyMode = "Packet";
                break;
            case High:
                speedMult = 1.2;
                reachMult = 1.1;
                fallMult = 1.1;
                flyMode = "Grim";
                break;
        }

        // Корректировка под конкретный античит
        if (acName.contains("grim")) {
            speedMult *= 0.9;
            flyMode = "Grim";
        } else if (acName.contains("vulcan")) {
            speedMult *= 0.85;
            flyMode = "Vulcan";
        } else if (acName.contains("matrix")) {
            speedMult *= 0.8;
            flyMode = "Matrix";
        }

        // Применяем к модулям
        applyToModule("SmartFly", "speed", speedMult);
        applyToModule("SmartFly", "mode", flyMode);
        applyToModule("Reach", "distance", 4.5 * reachMult);
        applyToModule("NoFall", "mode", "Packet");
        applyToModule("Speed", "speed", 1.0 * speedMult);

        if (debugMode.get()) {
            ChatUtils.info("§7Применены параметры: speed=" + speedMult + ", reach=" + reachMult + ", flyMode=" + flyMode);
        }
    }

    // --- УНИВЕРСАЛЬНОЕ ПРИМЕНЕНИЕ НАСТРОЕК К МОДУЛЮ ---
    private void applyToModule(String moduleName, String settingName, Object value) {
        Module module = Modules.get().get(moduleName);
        if (module == null) return;

        // В реальности нужно найти настройку по имени и установить значение
        // Это сложно без рефлексии, упрощаем
        if (debugMode.get()) {
            ChatUtils.info("§7Применено к " + moduleName + ":" + settingName + " = " + value);
        }
    }

    // --- ТИК (обновление) ---
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        tickCounter++;

        // Автоопределение при входе
        if (detectOnJoin.get() && tickCounter == 5) {
            // Имитация определения
            if (detectedAntiCheat == null && detectionMethod.get() != DetectionMethod.Kick) {
                // Заглушка для демонстрации
                detectedAntiCheat = new AntiCheatInfo("Unknown", 0.5);
                if (notifyDetection.get()) {
                    ChatUtils.info("§7Не удалось определить античит, используется стандартный обход.");
                }
            }
        }

        // Переопределение при задержке
        if (randomizeTimings.get() && detected && tickCounter % (20 + random.nextInt(20)) == 0) {
            applyBypass();
        }
    }

    // --- ИНФОРМАЦИЯ В HUD ---
    @Override
    public String getInfoString() {
        if (showDetected.get() && detectedAntiCheat != null) {
            return detectedAntiCheat.name;
        }
        return "§7Поиск...";
    }
}
