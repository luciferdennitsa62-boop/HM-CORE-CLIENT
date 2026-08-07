package hmcore.modules.movement;

import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import hmcore.HM_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ServerSideClickTP extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBypass = settings.createGroup("Bypass");
    private final SettingGroup sgVisual = settings.createGroup("Visual");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Режим телепортации")
            .defaultValue(Mode.Packet)
            .build()
    );

    public final Setting<Double> maxDistance = sgGeneral.add(new DoubleSetting.Builder()
            .name("max-distance")
            .description("Максимальная дистанция телепортации")
            .defaultValue(10.0)
            .min(1.0)
            .max(100.0)
            .sliderMax(100.0)
            .build()
    );

    public final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
            .name("delay")
            .description("Задержка перед телепортом (мс)")
            .defaultValue(0)
            .min(0)
            .max(1000)
            .sliderMax(1000)
            .build()
    );

    public final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
            .name("cooldown")
            .description("Задержка между телепортами (мс)")
            .defaultValue(500)
            .min(0)
            .max(5000)
            .sliderMax(5000)
            .build()
    );

    public final Setting<Boolean> onlyAir = sgGeneral.add(new BoolSetting.Builder()
            .name("only-air")
            .description("Телепортироваться только в пустое место")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> onlyVisible = sgGeneral.add(new BoolSetting.Builder()
            .name("only-visible")
            .description("Телепортироваться только к видимым блокам")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> centerPlayer = sgGeneral.add(new BoolSetting.Builder()
            .name("center-player")
            .description("Центрировать игрока после телепорта")
            .defaultValue(true)
            .build()
    );

    // --- НАСТРОЙКИ ОБХОДА ---

    public final Setting<BypassMode> bypass = sgBypass.add(new EnumSetting.Builder<BypassMode>()
            .name("bypass")
            .description("Метод обхода античита")
            .defaultValue(BypassMode.None)
            .build()
    );

    public final Setting<Integer> packetCount = sgBypass.add(new IntSetting.Builder()
            .name("packet-count")
            .description("Количество пакетов для отправки (Packet режим)")
            .defaultValue(1)
            .min(1)
            .max(20)
            .sliderMax(20)
            .build()
    );

    public final Setting<Boolean> useFakePosition = sgBypass.add(new BoolSetting.Builder()
            .name("use-fake-position")
            .description("Отправлять фейковые позиции перед телепортом")
            .defaultValue(true)
            .build()
    );

    public final Setting<Double> fakeOffset = sgBypass.add(new DoubleSetting.Builder()
            .name("fake-offset")
            .description("Смещение для фейковой позиции")
            .defaultValue(0.1)
            .min(0.01)
            .max(1.0)
            .sliderMax(1.0)
            .build()
    );

    // --- ВИЗУАЛЬНЫЕ НАСТРОЙКИ ---

    public final Setting<Boolean> renderBox = sgVisual.add(new BoolSetting.Builder()
            .name("render-box")
            .description("Показывать место телепорта")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> notify = sgVisual.add(new BoolSetting.Builder()
            .name("notify")
            .description("Уведомлять в чат о телепорте")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> particles = sgVisual.add(new BoolSetting.Builder()
            .name("particles")
            .description("Показывать частицы при телепорте")
            .defaultValue(true)
            .build()
    );

    // --- ПРОДВИНУТЫЕ НАСТРОЙКИ ---

    public final Setting<Boolean> autoDisableAfter = sgAdvanced.add(new BoolSetting.Builder()
            .name("auto-disable-after")
            .description("Автоматически выключить модуль после телепорта")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> blinkMode = sgAdvanced.add(new BoolSetting.Builder()
            .name("blink-mode")
            .description("Режим накопления пакетов (Blink)")
            .defaultValue(false)
            .build()
    );

    public final Setting<Integer> blinkTime = sgAdvanced.add(new IntSetting.Builder()
            .name("blink-time")
            .description("Время накопления пакетов в Blink режиме (мс)")
            .defaultValue(500)
            .min(100)
            .max(5000)
            .sliderMax(5000)
            .build()
    );

    // --- ENUM'Ы ---

    public enum Mode {
        Packet("Пакетный"),
        Vanilla("Ванильный"),
        Grim("GrimAC"),
        Vulcan("Vulcan");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum BypassMode {
        None("Выкл"),
        AntiTeleport("Анти-телепорт"),
        PacketSpoof("Подделка пакетов"),
        Full("Полный обход")
    }

    // --- ПЕРЕМЕННЫЕ ---

    private long lastTeleportTime = 0;
    private BlockPos targetPos = null;
    private long blinkStartTime = 0;
    private final List<PlayerMoveC2SPacket> blinkPackets = new ArrayList<>();
    private final Random random = new Random();

    // --- КОНСТРУКТОР ---

    public ServerSideClickTP() {
        super(HM_CORE.CATEGORY, "ServerSideClickTP", "Телепорт к блоку через серверные пакеты");
    }

    @Override
    public void onActivate() {
        lastTeleportTime = 0;
        blinkPackets.clear();
        blinkStartTime = 0;
    }

    @Override
    public void onDeactivate() {
        blinkPackets.clear();
        targetPos = null;
    }

    // --- ОСНОВНОЙ ТИК ---

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Blink режим
        if (blinkMode.get()) {
            handleBlink();
        }

        // Отображение места телепорта
        if (renderBox.get() && targetPos != null) {
            // Здесь можно отрисовать куб
        }
    }

    // --- ОБРАБОТКА КЛИКА ПО БЛОКУ ---

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return;

        // Проверка задержки
        if (System.currentTimeMillis() - lastTeleportTime < cooldown.get()) {
            if (notify.get()) {
                ChatUtils.warn("Подожди " + (cooldown.get() / 1000) + " сек перед следующим телепортом");
            }
            return;
        }

        // Получаем позицию блока
        BlockHitResult hit = (BlockHitResult) mc.crosshairTarget;
        BlockPos pos = hit.getBlockPos().offset(hit.getSide());
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;

        // Проверка дистанции
        double distance = mc.player.squaredDistanceTo(x, y, z);
        if (distance > maxDistance.get() * maxDistance.get()) {
            if (notify.get()) {
                ChatUtils.warn("Слишком далеко! Макс: " + maxDistance.get() + " блоков");
            }
            return;
        }

        // Проверка на воздух
        if (onlyAir.get()) {
            Block block = mc.world.getBlockState(pos).getBlock();
            if (!block.equals(Blocks.AIR) && !block.equals(Blocks.CAVE_AIR) && !block.equals(Blocks.VOID_AIR)) {
                if (notify.get()) {
                    ChatUtils.warn("Там не воздух!");
                }
                return;
            }
        }

        // Проверка видимости
        if (onlyVisible.get() && !mc.player.canSee(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
            if (notify.get()) {
                ChatUtils.warn("Блок не виден!");
            }
            return;
        }

        targetPos = pos;

        // --- ВЫПОЛНЕНИЕ ТЕЛЕПОРТА ---

        // Задержка перед телепортом
        if (delay.get() > 0) {
            try {
                Thread.sleep(delay.get());
            } catch (InterruptedException ignored) {}
        }

        // Телепортируем
        switch (mode.get()) {
            case Packet -> teleportPacket(x, y, z);
            case Vanilla -> teleportVanilla(x, y, z);
            case Grim -> teleportGrim(x, y, z);
            case Vulcan -> teleportVulcan(x, y, z);
        }

        // Центрирование игрока
        if (centerPlayer.get()) {
            double centerX = pos.getX() + 0.5;
            double centerZ = pos.getZ() + 0.5;
            mc.player.setPosition(centerX, y, centerZ);
        }

        lastTeleportTime = System.currentTimeMillis();

        // Уведомление
        if (notify.get()) {
            ChatUtils.info("Телепорт выполнен на расстояние: " + String.format("%.1f", Math.sqrt(distance)) + " блоков");
        }

        // Автовыключение
        if (autoDisableAfter.get()) {
            toggle();
            ChatUtils.info("ServerSideClickTP выключен (авто)");
        }
    }

    // --- МЕТОДЫ ТЕЛЕПОРТАЦИИ ---

    private void teleportPacket(double x, double y, double z) {
        if (mc.player == null) return;

        // Отправляем пакеты
        for (int i = 0; i < packetCount.get(); i++) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, true));
        }

        // Фейковая позиция
        if (useFakePosition.get()) {
            double fakeY = y + fakeOffset.get() * random.nextDouble();
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, fakeY, z, true));
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, true));
        }

        // Установка позиции
        mc.player.setPosition(x, y, z);
    }

    private void teleportVanilla(double x, double y, double z) {
        if (mc.player == null) return;

        // Простая установка позиции
        mc.player.setPosition(x, y, z);
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, true));
    }

    private void teleportGrim(double x, double y, double z) {
        if (mc.player == null) return;

        // Grim требует специфических значений
        double currentY = mc.player.getY();

        // Поднимаем на 0.001
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), currentY + 0.001, mc.player.getZ(), false
        ));

        // Телепортируем
        mc.player.setPosition(x, y, z);
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, true));

        // Возвращаем на место
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), currentY, mc.player.getZ(), true
        ));
    }

    private void teleportVulcan(double x, double y, double z) {
        if (mc.player == null) return;

        // Vulcan: отправляем несколько пакетов с маленьким смещением
        double stepY = mc.player.getY();

        for (int i = 0; i < 10; i++) {
            stepY += 0.1;
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), stepY, mc.player.getZ(), false
            ));
        }

        mc.player.setPosition(x, y, z);
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, true));
    }

    // --- BLINK РЕЖИМ ---

    private void handleBlink() {
        if (mc.player == null) return;

        if (blinkStartTime == 0) {
            blinkStartTime = System.currentTimeMillis();
            blinkPackets.clear();
        }

        // Накопление пакетов
        if (System.currentTimeMillis() - blinkStartTime < blinkTime.get()) {
            // Сохраняем пакеты движения
            blinkPackets.add(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.isOnGround()
            ));
        } else {
            // Отправляем все накопленные пакеты
            for (PlayerMoveC2SPacket packet : blinkPackets) {
                mc.player.networkHandler.sendPacket(packet);
            }
            blinkPackets.clear();
            blinkStartTime = 0;
        }
    }

    // --- ВИЗУАЛИЗАЦИЯ (заглушка) ---

    @EventHandler
    private void onTickRender(TickEvent.Post event) {
        if (renderBox.get() && targetPos != null && mc.world != null) {
            // Здесь можно отрисовать куб на месте телепорта
            // В Meteor это делается через RenderUtils
        }
    }
}
