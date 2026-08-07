package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import hmcore.HM_CORE;

import java.util.Random;

public class AntiVoid extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTeleport = settings.createGroup("Teleport");
    private final SettingGroup sgBypass = settings.createGroup("Bypass");
    private final SettingGroup sgVisual = settings.createGroup("Visual");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Double> triggerHeight = sgGeneral.add(new DoubleSetting.Builder()
            .name("trigger-height")
            .description("Высота, на которой срабатывает защита (Y)")
            .defaultValue(-10.0)
            .min(-1000.0)
            .max(100.0)
            .sliderMax(100.0)
            .build()
    );

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Режим защиты")
            .defaultValue(Mode.Teleport)
            .build()
    );

    public final Setting<Integer> teleportDelay = sgGeneral.add(new IntSetting.Builder()
            .name("teleport-delay")
            .description("Задержка перед телепортом (мс)")
            .defaultValue(0)
            .min(0)
            .max(500)
            .sliderMax(500)
            .build()
    );

    // --- ТЕЛЕПОРТ ---

    public final Setting<TeleportTarget> teleportTarget = sgTeleport.add(new EnumSetting.Builder<TeleportTarget>()
            .name("teleport-target")
            .description("Куда телепортироваться")
            .defaultValue(TeleportTarget.Spawn)
            .build()
    );

    public final Setting<Double> teleportY = sgTeleport.add(new DoubleSetting.Builder()
            .name("teleport-y")
            .description("Высота телепортации (для Custom режима)")
            .defaultValue(80.0)
            .min(0.0)
            .max(256.0)
            .sliderMax(256.0)
            .build()
    );

    public final Setting<Boolean> centerPlayer = sgTeleport.add(new BoolSetting.Builder()
            .name("center-player")
            .description("Центрировать игрока при телепорте")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> savePosition = sgTeleport.add(new BoolSetting.Builder()
            .name("save-position")
            .description("Сохранять текущую позицию для быстрого возврата")
            .defaultValue(false)
            .build()
    );

    // --- ОБХОД ---

    public final Setting<BypassType> bypass = sgBypass.add(new EnumSetting.Builder<BypassType>()
            .name("bypass")
            .description("Метод обхода античита")
            .defaultValue(BypassType.None)
            .build()
    );

    public final Setting<Integer> packetCount = sgBypass.add(new IntSetting.Builder()
            .name("packet-count")
            .description("Количество пакетов для отправки")
            .defaultValue(3)
            .min(1)
            .max(10)
            .sliderMax(10)
            .build()
    );

    public final Setting<Double> fakeOffset = sgBypass.add(new DoubleSetting.Builder()
            .name("fake-offset")
            .description("Смещение для подделки позиции")
            .defaultValue(0.1)
            .min(0.01)
            .max(1.0)
            .sliderMax(1.0)
            .build()
    );

    // --- ВИЗУАЛ ---

    public final Setting<Boolean> render = sgVisual.add(new BoolSetting.Builder()
            .name("render")
            .description("Показывать место телепорта")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> notify = sgVisual.add(new BoolSetting.Builder()
            .name("notify")
            .description("Уведомлять о срабатывании")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> showStatus = sgVisual.add(new BoolSetting.Builder()
            .name("show-status")
            .description("Показывать статус в HUD")
            .defaultValue(true)
            .build()
    );

    // --- ПРОДВИНУТЫЕ ---

    public final Setting<Boolean> autoDisableAfter = sgAdvanced.add(new BoolSetting.Builder()
            .name("auto-disable-after")
            .description("Автоматически выключить модуль после срабатывания")
            .defaultValue(false)
            .build()
    );

    public final Setting<Integer> cooldown = sgAdvanced.add(new IntSetting.Builder()
            .name("cooldown")
            .description("Задержка после срабатывания (мс)")
            .defaultValue(5000)
            .min(0)
            .max(30000)
            .sliderMax(30000)
            .build()
    );

    public final Setting<Boolean> resetFallDistance = sgAdvanced.add(new BoolSetting.Builder()
            .name("reset-fall-distance")
            .description("Сбрасывать дистанцию падения")
            .defaultValue(true)
            .build()
    );

    // --- ENUM'Ы ---
    public enum Mode {
        Teleport("Телепорт"),
        Packet("Пакетный"),
        Respawn("Респавн"),
        Climb("Подъём");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum TeleportTarget {
        Spawn("Спавн"),
        Custom("Пользовательский"),
        LastPosition("Последняя позиция");

        private final String name;

        TeleportTarget(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum BypassType {
        None("Выкл"),
        Spoof("Подделка позиции"),
        Full("Полный обход");

        private final String name;

        BypassType(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // --- ПЕРЕМЕННЫЕ ---
    private long lastTriggerTime = 0;
    private BlockPos savedPosition = null;
    private double savedY = 0;
    private final Random random = new Random();

    // --- КОНСТРУКТОР ---
    public AntiVoid() {
        super(HM_CORE.CATEGORY, "AntiVoid", "Защита от падения в пустоту");
    }

    @Override
    public void onActivate() {
        lastTriggerTime = 0;
        savedPosition = null;
        if (mc.player != null) {
            savedY = mc.player.getY();
        }
        if (notify.get()) {
            ChatUtils.info("AntiVoid активирован. Высота срабатывания: " + triggerHeight.get());
        }
    }

    @Override
    public void onDeactivate() {
        savedPosition = null;
    }

    // --- ТИК ---
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Проверка задержки
        if (System.currentTimeMillis() - lastTriggerTime < cooldown.get()) return;

        // Сохранение позиции
        if (savePosition.get()) {
            savedPosition = mc.player.getBlockPos();
        }

        // Проверка высоты
        if (mc.player.getY() > triggerHeight.get()) return;

        // --- СРАБАТЫВАНИЕ ---
        if (notify.get()) {
            ChatUtils.warn("§cAntiVoid сработал! Высота: " + String.format("%.1f", mc.player.getY()));
        }

        switch (mode.get()) {
            case Teleport -> teleport();
            case Packet -> teleportPacket();
            case Respawn -> respawn();
            case Climb -> climb();
        }

        lastTriggerTime = System.currentTimeMillis();

        // Автовыключение
        if (autoDisableAfter.get()) {
            toggle();
            ChatUtils.info("AntiVoid выключен (авто)");
        }
    }

    // --- ТЕЛЕПОРТ ---
    private void teleport() {
        if (mc.player == null) return;

        double x = mc.player.getX();
        double z = mc.player.getZ();
        double y = 0;

        switch (teleportTarget.get()) {
            case Spawn -> {
                // Получаем спавн мира
                BlockPos spawn = mc.world.getSpawnPos();
                x = spawn.getX() + 0.5;
                z = spawn.getZ() + 0.5;
                y = spawn.getY() + 1.0;
            }
            case Custom -> {
                y = teleportY.get();
            }
            case LastPosition -> {
                if (savedPosition != null) {
                    x = savedPosition.getX() + 0.5;
                    z = savedPosition.getZ() + 0.5;
                    y = savedPosition.getY() + 1.0;
                } else {
                    y = 80.0;
                }
            }
        }

        if (centerPlayer.get()) {
            x = Math.floor(x) + 0.5;
            z = Math.floor(z) + 0.5;
        }

        // Задержка
        if (teleportDelay.get() > 0) {
            try {
                Thread.sleep(teleportDelay.get());
            } catch (InterruptedException ignored) {}
        }

        // Установка позиции
        mc.player.setPosition(x, y, z);
        if (resetFallDistance.get()) {
            mc.player.fallDistance = 0;
        }
    }

    // --- ПАКЕТНЫЙ ТЕЛЕПОРТ ---
    private void teleportPacket() {
        if (mc.player == null) return;

        double x = mc.player.getX();
        double z = mc.player.getZ();
        double y = 80.0;

        if (teleportTarget.get() == TeleportTarget.Spawn) {
            BlockPos spawn = mc.world.getSpawnPos();
            x = spawn.getX() + 0.5;
            z = spawn.getZ() + 0.5;
            y = spawn.getY() + 1.0;
        } else if (teleportTarget.get() == TeleportTarget.Custom) {
            y = teleportY.get();
        }

        if (centerPlayer.get()) {
            x = Math.floor(x) + 0.5;
            z = Math.floor(z) + 0.5;
        }

        // Отправка пакетов
        for (int i = 0; i < packetCount.get(); i++) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, true));
        }

        // Подделка позиции (обход)
        if (bypass.get() != BypassType.None) {
            double fakeY = y + fakeOffset.get() * (random.nextDouble() + 0.5);
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, fakeY, z, false));
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, true));
        }

        mc.player.setPosition(x, y, z);
        if (resetFallDistance.get()) {
            mc.player.fallDistance = 0;
        }
    }

    // --- РЕСПАВН ---
    private void respawn() {
        if (mc.player == null) return;
        mc.player.setHealth(20.0f);
        mc.player.setPosition(mc.player.getX(), 10, mc.player.getZ());
        if (resetFallDistance.get()) {
            mc.player.fallDistance = 0;
        }
    }

    // --- ПОДЪЁМ ---
    private void climb() {
        if (mc.player == null) return;
        // Поднимаем игрока вверх, пока не достигнет безопасной высоты
        double targetY = teleportTarget.get() == TeleportTarget.Custom ? teleportY.get() : 80.0;
        mc.player.setVelocity(mc.player.getVelocity().x, 5.0, mc.player.getVelocity().z);
        // В следующем тике проверим, достиг ли он нужной высоты
        if (mc.player.getY() > targetY) {
            mc.player.setVelocity(0, 0, 0);
        }
        if (resetFallDistance.get()) {
            mc.player.fallDistance = 0;
        }
    }

    // --- ИНФОРМАЦИЯ В HUD ---
    @Override
    public String getInfoString() {
        if (showStatus.get() && mc.player != null) {
            if (mc.player.getY() < triggerHeight.get() + 5) {
                return "§cОпасно!";
            } else {
                return "§aБезопасно";
            }
        }
        return null;
    }
}
