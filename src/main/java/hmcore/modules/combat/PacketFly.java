package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;
import hmcore.HM_CORE;

import java.util.Random;

public class PacketFly extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBypass = settings.createGroup("Bypass");
    private final SettingGroup sgVisual = settings.createGroup("Visual");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Режим полета")
            .defaultValue(Mode.Packet)
            .build()
    );

    public final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
            .name("speed")
            .description("Горизонтальная скорость")
            .defaultValue(1.0)
            .min(0.1)
            .max(5.0)
            .sliderMax(5.0)
            .build()
    );

    public final Setting<Double> verticalSpeed = sgGeneral.add(new DoubleSetting.Builder()
            .name("vertical-speed")
            .description("Вертикальная скорость")
            .defaultValue(0.5)
            .min(0.1)
            .max(2.0)
            .sliderMax(2.0)
            .build()
    );

    public final Setting<Integer> packetCount = sgGeneral.add(new IntSetting.Builder()
            .name("packet-count")
            .description("Количество пакетов за тик (чем больше, тем быстрее, но рискованнее)")
            .defaultValue(3)
            .min(1)
            .max(10)
            .sliderMax(10)
            .build()
    );

    public final Setting<Boolean> antiKick = sgGeneral.add(new BoolSetting.Builder()
            .name("anti-kick")
            .description("Защита от кика за полет")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> glide = sgGeneral.add(new BoolSetting.Builder()
            .name("glide")
            .description("Плавное снижение")
            .defaultValue(false)
            .build()
    );

    // --- НАСТРОЙКИ ОБХОДА ---

    public final Setting<BypassType> bypass = sgBypass.add(new EnumSetting.Builder<BypassType>()
            .name("bypass")
            .description("Тип обхода античита")
            .defaultValue(BypassType.Spoof)
            .build()
    );

    public final Setting<Integer> spoofOffset = sgBypass.add(new IntSetting.Builder()
            .name("spoof-offset")
            .description("Смещение для подделки позиции (в тиках)")
            .defaultValue(3)
            .min(1)
            .max(10)
            .sliderMax(10)
            .build()
    );

    public final Setting<Double> fallDistance = sgBypass.add(new DoubleSetting.Builder()
            .name("fall-distance")
            .description("Дистанция падения для обмана античита")
            .defaultValue(0.01)
            .min(0.001)
            .max(1.0)
            .sliderMax(1.0)
            .build()
    );

    // --- ВИЗУАЛ ---

    public final Setting<Boolean> renderSpeed = sgVisual.add(new BoolSetting.Builder()
            .name("render-speed")
            .description("Показывать скорость в HUD")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> notifyToggle = sgVisual.add(new BoolSetting.Builder()
            .name("notify-toggle")
            .description("Уведомления о включении/выключении")
            .defaultValue(true)
            .build()
    );

    // --- ПРОДВИНУТЫЕ ---

    public final Setting<Boolean> smartDirection = sgAdvanced.add(new BoolSetting.Builder()
            .name("smart-direction")
            .description("Учитывать направление взгляда")
            .defaultValue(true)
            .build()
    );

    public final Setting<Integer> autoDisableTime = sgAdvanced.add(new IntSetting.Builder()
            .name("auto-disable-time")
            .description("Автовыключение через X секунд (0 = выкл)")
            .defaultValue(0)
            .min(0)
            .max(300)
            .sliderMax(300)
            .build()
    );

    public final Setting<Boolean> bindJump = sgAdvanced.add(new BoolSetting.Builder()
            .name("bind-jump")
            .description("Использовать прыжок для подъема")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> bindSneak = sgAdvanced.add(new BoolSetting.Builder()
            .name("bind-sneak")
            .description("Использовать приседание для спуска")
            .defaultValue(true)
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

    public enum BypassType {
        None("Выкл"),
        Spoof("Подделка позиции"),
        AntiKick("Анти-кик"),
        Full("Полный обход")
    }

    // --- ПЕРЕМЕННЫЕ ---
    private long startTime = 0;
    private int tickCounter = 0;
    private final Random random = new Random();
    private boolean isFlying = false;

    // --- КОНСТРУКТОР ---
    public PacketFly() {
        super(HM_CORE.CATEGORY, "PacketFly", "Полет через подделку пакетов (обходит античиты)");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;
        startTime = System.currentTimeMillis();
        tickCounter = 0;
        isFlying = false;

        if (mode.get() == Mode.Vanilla) {
            mc.player.getAbilities().flying = true;
            mc.player.getAbilities().setFlySpeed(speed.get().floatValue() / 10f);
        }

        if (notifyToggle.get()) {
            ChatUtils.info("PacketFly включен. Режим: " + mode.get());
        }
    }

    @Override
    public void onDeactivate() {
        if (mc.player == null) return;

        mc.player.getAbilities().flying = false;
        mc.player.getAbilities().setFlySpeed(0.05f);
        mc.player.setVelocity(0, -0.5, 0);

        if (notifyToggle.get()) {
            ChatUtils.info("PacketFly выключен.");
        }
    }

    // --- ТИК ---
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Автовыключение
        if (autoDisableTime.get() > 0) {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            if (elapsed > autoDisableTime.get()) {
                toggle();
                ChatUtils.warn("PacketFly автоматически выключен (таймер)");
                return;
            }
        }

        // Обработка режимов
        switch (mode.get()) {
            case Vanilla -> handleVanilla();
            case Packet -> handlePacket();
            case Grim -> handleGrim();
            case Vulcan -> handleVulcan();
        }

        // Анти-кик
        if (antiKick.get() && mc.player.age % 20 == 0) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
        }

        tickCounter++;
    }

    // --- ОБРАБОТЧИКИ ---

    private void handleVanilla() {
        mc.player.getAbilities().flying = true;
        mc.player.getAbilities().setFlySpeed(speed.get().floatValue() / 10f);

        if (bindJump.get() && mc.options.jumpKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, verticalSpeed.get(), mc.player.getVelocity().z);
        }
        if (bindSneak.get() && mc.options.sneakKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, -verticalSpeed.get(), mc.player.getVelocity().z);
        }
    }

    private void handlePacket() {
        mc.player.getAbilities().flying = false;

        Vec3d forward = getMovementVector();
        if (forward.length() > 0) {
            Vec3d newPos = mc.player.getPos().add(forward);

            // Отправляем пакеты
            for (int i = 0; i < packetCount.get(); i++) {
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        newPos.x, newPos.y, newPos.z, false
                ));
            }

            // Подделка позиции (обход)
            if (bypass.get() == BypassType.Spoof) {
                double fakeY = newPos.y + fallDistance.get() * random.nextDouble();
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        newPos.x, fakeY, newPos.z, false
                ));
            }

            mc.player.setPosition(newPos);
            isFlying = true;
        } else {
            if (bypass.get() != BypassType.None) {
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
            }
            isFlying = false;
        }
    }

    private void handleGrim() {
        mc.player.getAbilities().flying = false;

        Vec3d forward = getMovementVector();
        if (forward.length() > 0) {
            Vec3d newPos = mc.player.getPos().add(forward.multiply(0.7));

            // Grim требует специфических значений
            double offset = 0.001091981 + random.nextDouble() * 0.0001;
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    newPos.x, newPos.y + offset, newPos.z, false
            ));

            mc.player.setPosition(newPos);
        }

        if (mc.player.age % 5 == 0) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
        }

        if (glide.get()) {
            mc.player.setVelocity(mc.player.getVelocity().x, -0.01, mc.player.getVelocity().z);
        }
    }

    private void handleVulcan() {
        mc.player.getAbilities().flying = true;
        mc.player.getAbilities().setFlySpeed(0.05f);

        // Vulcan требует постоянной отправки пакетов "на земле"
        if (mc.player.age % 2 == 0) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), mc.player.getY() - 0.0001, mc.player.getZ(), true
            ));
        }

        if (bindJump.get() && mc.options.jumpKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, verticalSpeed.get() / 2, mc.player.getVelocity().z);
        }
        if (bindSneak.get() && mc.options.sneakKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, -verticalSpeed.get() / 2, mc.player.getVelocity().z);
        }
    }

    // --- ПОЛУЧЕНИЕ ВЕКТОРА ДВИЖЕНИЯ ---
    private Vec3d getMovementVector() {
        Vec3d forward = new Vec3d(0, 0, 0);
        double speedFactor = speed.get() / 10;

        if (mc.options.forwardKey.isPressed()) {
            forward = forward.add(mc.player.getRotationVector().multiply(speedFactor));
        }
        if (mc.options.backKey.isPressed()) {
            forward = forward.subtract(mc.player.getRotationVector().multiply(speedFactor));
        }
        if (mc.options.leftKey.isPressed()) {
            forward = forward.add(mc.player.getRotationVector().rotateY(-(float) Math.toRadians(90)).multiply(speedFactor));
        }
        if (mc.options.rightKey.isPressed()) {
            forward = forward.add(mc.player.getRotationVector().rotateY((float) Math.toRadians(90)).multiply(speedFactor));
        }

        if (bindJump.get() && mc.options.jumpKey.isPressed()) {
            forward = forward.add(0, verticalSpeed.get() / 10, 0);
        }
        if (bindSneak.get() && mc.options.sneakKey.isPressed()) {
            forward = forward.add(0, -verticalSpeed.get() / 10, 0);
        }

        return forward;
    }

    @Override
    public String getInfoString() {
        if (isFlying) {
            return "§a" + String.format("%.1f", speed.get()) + " блок/с";
        } else {
            return "§7Ожидание";
        }
    }
}
