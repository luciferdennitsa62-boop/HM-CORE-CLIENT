package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import hmcore.HM_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BlinkTP extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBypass = settings.createGroup("Bypass");
    private final SettingGroup sgVisual = settings.createGroup("Visual");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Integer> bufferTime = sgGeneral.add(new IntSetting.Builder()
            .name("buffer-time")
            .description("Время накопления пакетов (мс)")
            .defaultValue(500)
            .min(100)
            .max(5000)
            .sliderMax(5000)
            .build()
    );

    public final Setting<Double> maxDistance = sgGeneral.add(new DoubleSetting.Builder()
            .name("max-distance")
            .description("Максимальная дистанция телепорта")
            .defaultValue(10.0)
            .min(1.0)
            .max(50.0)
            .sliderMax(50.0)
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

    public final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
            .name("cooldown")
            .description("Задержка между телепортами (мс)")
            .defaultValue(1000)
            .min(0)
            .max(5000)
            .sliderMax(5000)
            .build()
    );

    // --- НАСТРОЙКИ ОБХОДА ---

    public final Setting<BypassMode> bypass = sgBypass.add(new EnumSetting.Builder<BypassMode>()
            .name("bypass")
            .description("Метод обхода античита")
            .defaultValue(BypassMode.Spoof)
            .build()
    );

    public final Setting<Integer> packetCount = sgBypass.add(new IntSetting.Builder()
            .name("packet-count")
            .description("Количество пакетов для отправки при телепорте")
            .defaultValue(3)
            .min(1)
            .max(10)
            .sliderMax(10)
            .build()
    );

    public final Setting<Double> spoofOffset = sgBypass.add(new DoubleSetting.Builder()
            .name("spoof-offset")
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
            .description("Уведомлять о телепорте")
            .defaultValue(true)
            .build()
    );

    // --- ПРОДВИНУТЫЕ ---

    public final Setting<Boolean> autoDisable = sgAdvanced.add(new BoolSetting.Builder()
            .name("auto-disable")
            .description("Автоматически выключать модуль после телепорта")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> randomOffset = sgAdvanced.add(new BoolSetting.Builder()
            .name("random-offset")
            .description("Случайное смещение (для обхода античита)")
            .defaultValue(true)
            .build()
    );

    // --- ENUM ---
    public enum BypassMode {
        None("Выкл"),
        Spoof("Подделка позиции"),
        AntiKick("Анти-кик"),
        Full("Полный обход");

        private final String name;

        BypassMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // --- ПЕРЕМЕННЫЕ ---
    private long startTime = 0;
    private long lastTeleportTime = 0;
    private final List<PlayerMoveC2SPacket> packets = new ArrayList<>();
    private BlockPos targetPos = null;
    private boolean isBuffering = false;
    private final Random random = new Random();

    // --- КОНСТРУКТОР ---
    public BlinkTP() {
        super(HM_CORE.CATEGORY, "BlinkTP", "Телепорт с накоплением пакетов (обход античита)");
    }

    @Override
    public void onActivate() {
        startTime = System.currentTimeMillis();
        lastTeleportTime = 0;
        packets.clear();
        targetPos = null;
        isBuffering = true;
        if (notify.get()) {
            ChatUtils.info("BlinkTP активирован. Буферизация пакетов...");
        }
    }

    @Override
    public void onDeactivate() {
        // Если модуль выключается, отправляем все накопленные пакеты
        if (!packets.isEmpty()) {
            flushPackets();
        }
        isBuffering = false;
        targetPos = null;
        if (notify.get()) {
            ChatUtils.info("BlinkTP деактивирован");
        }
    }

    // --- ТИК ---
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Проверка задержки между телепортами
        if (System.currentTimeMillis() - lastTeleportTime < cooldown.get()) return;

        // Получаем блок под прицелом
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            BlockHitResult hit = (BlockHitResult) mc.crosshairTarget;
            targetPos = hit.getBlockPos().offset(hit.getSide());
        } else {
            targetPos = null;
            return;
        }

        if (targetPos == null) return;

        // Проверка на воздух
        if (onlyAir.get() && !mc.world.getBlockState(targetPos).isAir()) {
            if (notify.get() && mc.player.age % 20 == 0) {
                ChatUtils.warn("Цель не воздух! Подожди...");
            }
            return;
        }

        // Проверка видимости
        if (onlyVisible.get() && !mc.player.canSee(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5)) {
            if (notify.get() && mc.player.age % 20 == 0) {
                ChatUtils.warn("Блок не виден!");
            }
            return;
        }

        // Проверка дистанции
        double x = targetPos.getX() + 0.5;
        double y = targetPos.getY() + 1.0;
        double z = targetPos.getZ() + 0.5;
        if (mc.player.squaredDistanceTo(x, y, z) > maxDistance.get() * maxDistance.get()) {
            if (notify.get() && mc.player.age % 20 == 0) {
                ChatUtils.warn("Слишком далеко! Макс: " + maxDistance.get() + " блоков");
            }
            return;
        }

        // --- БУФЕРИЗАЦИЯ ---
        if (isBuffering) {
            // Накопление пакетов
            if (System.currentTimeMillis() - startTime < bufferTime.get()) {
                // Сохраняем текущее состояние движения
                packets.add(new PlayerMoveC2SPacket.PositionAndOnGround(
                        mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.isOnGround()
                ));
                // Также добавляем промежуточные пакеты для обмана
                if (bypass.get() == BypassMode.Spoof && packets.size() % 2 == 0) {
                    double fakeY = mc.player.getY() + (randomOffset.get() ? random.nextDouble() * spoofOffset.get() : spoofOffset.get());
                    packets.add(new PlayerMoveC2SPacket.PositionAndOnGround(
                            mc.player.getX(), fakeY, mc.player.getZ(), false
                    ));
                }
            } else {
                // Время буферизации закончилось — выполняем телепорт
                performTeleport(x, y, z);
                isBuffering = false;
                if (autoDisable.get()) {
                    toggle();
                    ChatUtils.info("BlinkTP выключен (авто)");
                }
            }
        }
    }

    // --- ВЫПОЛНЕНИЕ ТЕЛЕПОРТА ---
    private void performTeleport(double x, double y, double z) {
        if (mc.player == null) return;

        // Отправляем все накопленные пакеты
        flushPackets();

        // Отправляем пакет телепорта
        for (int i = 0; i < packetCount.get(); i++) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, true));
        }

        // Дополнительный обход (подделка позиции)
        if (bypass.get() == BypassMode.Full) {
            double fakeX = x + (randomOffset.get() ? (random.nextDouble() - 0.5) * 0.1 : 0.05);
            double fakeZ = z + (randomOffset.get() ? (random.nextDouble() - 0.5) * 0.1 : 0.05);
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(fakeX, y, fakeZ, false));
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, true));
        }

        // Установка позиции игрока
        mc.player.setPosition(x, y, z);

        lastTeleportTime = System.currentTimeMillis();
        startTime = System.currentTimeMillis(); // Сброс буферизации

        if (notify.get()) {
            ChatUtils.info("§aТелепорт выполнен! §7" + String.format("%.1f", Math.sqrt(mc.player.squaredDistanceTo(x, y, z))) + " блоков");
        }

        // Сброс буферизации для следующего телепорта
        packets.clear();
        isBuffering = true;
    }

    // --- ОТПРАВКА НАКОПЛЕННЫХ ПАКЕТОВ ---
    private void flushPackets() {
        if (mc.player == null) return;
        for (PlayerMoveC2SPacket packet : packets) {
            mc.player.networkHandler.sendPacket(packet);
        }
        packets.clear();
    }

    // --- ИНФОРМАЦИЯ В HUD ---
    @Override
    public String getInfoString() {
        if (isBuffering) {
            long elapsed = System.currentTimeMillis() - startTime;
            int progress = (int) (100 * elapsed / bufferTime.get());
            return "§6Буфер: " + progress + "%";
        } else {
            return "§aГотов";
        }
    }
}
