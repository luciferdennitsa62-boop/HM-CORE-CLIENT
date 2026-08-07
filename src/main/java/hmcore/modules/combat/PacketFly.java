package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import hmcore.HM_CORE;

public class PacketFly extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBypass = settings.createGroup("Bypass");
    private final SettingGroup sgVisual = settings.createGroup("Visual");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Режим полёта")
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

    public final Setting<Boolean> antiKick = sgGeneral.add(new BoolSetting.Builder()
            .name("anti-kick")
            .description("Предотвращает кик за полёт")
            .defaultValue(true)
            .build()
    );

    public final Setting<Integer> packetLimit = sgGeneral.add(new IntSetting.Builder()
            .name("packet-limit")
            .description("Количество пакетов за тик")
            .defaultValue(5)
            .min(1)
            .max(20)
            .sliderMax(20)
            .build()
    );

    // --- ОБХОД ---

    public final Setting<BypassType> bypass = sgBypass.add(new EnumSetting.Builder<BypassType>()
            .name("bypass")
            .description("Метод обхода античита")
            .defaultValue(BypassType.None)
            .build()
    );

    public final Setting<Boolean> useFakePosition = sgBypass.add(new BoolSetting.Builder()
            .name("use-fake-position")
            .description("Отправлять фейковую позицию перед движением")
            .defaultValue(true)
            .build()
    );

    public final Setting<Double> fakeOffset = sgBypass.add(new DoubleSetting.Builder()
            .name("fake-offset")
            .description("Смещение для фейковой позиции")
            .defaultValue(0.1)
            .min(0.01)
            .max(0.5)
            .sliderMax(0.5)
            .build()
    );

    public final Setting<Boolean> antiStuck = sgBypass.add(new BoolSetting.Builder()
            .name("anti-stuck")
            .description("Автоматически выходить из блоков")
            .defaultValue(true)
            .build()
    );

    // --- ВИЗУАЛ ---

    public final Setting<Boolean> renderSpeed = sgVisual.add(new BoolSetting.Builder()
            .name("render-speed")
            .description("Показывать скорость в HUD")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> trail = sgVisual.add(new BoolSetting.Builder()
            .name("trail")
            .description("Оставлять след из частиц")
            .defaultValue(false)
            .build()
    );

    // --- ENUM'Ы ---

    public enum Mode {
        Packet("Пакетный"),
        Grim("GrimAC"),
        Vulcan("Vulcan"),
        Matrix("Matrix"),
        Vanilla("Ванильный (креатив)"),
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

    public enum BypassType {
        None("Выкл"),
        AntiKick("Анти-кик"),
        PacketSpoof("Подделка пакетов"),
        Full("Полный обход")
    }

    // --- ПЕРЕМЕННЫЕ ---
    private int tickCounter = 0;
    private Vec3d startPos = null;

    // --- КОНСТРУКТОР ---
    public PacketFly() {
        super(HM_CORE.CATEGORY, "PacketFly", "Полет через подделку пакетов (обходит античиты)");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;
        startPos = mc.player.getPos();
        tickCounter = 0;
        // Для ванильного режима включаем креатив
        if (mode.get() == Mode.Vanilla) {
            mc.player.getAbilities().flying = true;
            mc.player.getAbilities().setFlySpeed(speed.get().floatValue() / 10f);
        }
    }

    @Override
    public void onDeactivate() {
        if (mc.player == null) return;
        // Выключаем креатив
        mc.player.getAbilities().flying = false;
        mc.player.getAbilities().setFlySpeed(0.05f);
        // Если в полёте, мягко приземляем
        if (!mc.player.isOnGround()) {
            mc.player.setVelocity(0, -0.5, 0);
        }
        startPos = null;
    }

    // --- ТИК ---
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        tickCounter++;

        // Анти-кик
        if (antiKick.get() && tickCounter % 20 == 0) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
        }

        // Anti-stuck
        if (antiStuck.get() && mc.player.horizontalCollision) {
            mc.player.setVelocity(mc.player.getVelocity().x, 0.1, mc.player.getVelocity().z);
        }

        // Обработка режимов
        switch (mode.get()) {
            case Vanilla -> handleVanilla();
            case Packet -> handlePacket();
            case Grim -> handleGrim();
            case Vulcan -> handleVulcan();
            case Matrix -> handleMatrix();
            case Custom -> handleCustom();
        }
    }

    // --- ОБРАБОТЧИКИ ---

    private void handleVanilla() {
        // Креативный полёт
        mc.player.getAbilities().flying = true;
        mc.player.getAbilities().setFlySpeed(speed.get().floatValue() / 10f);

        // Управление
        Vec3d movement = getMovementVector();
        if (movement.length() > 0) {
            mc.player.setVelocity(movement);
        }
    }

    private void handlePacket() {
        // Отключаем креатив
        mc.player.getAbilities().flying = false;

        Vec3d movement = getMovementVector();
        if (movement.length() > 0) {
            Vec3d newPos = mc.player.getPos().add(movement);

            // Фейковая позиция
            if (useFakePosition.get()) {
                double fakeY = newPos.y + fakeOffset.get();
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        newPos.x, fakeY, newPos.z, false
                ));
            }

            // Отправка пакетов
            for (int i = 0; i < packetLimit.get(); i++) {
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        newPos.x, newPos.y, newPos.z, false
                ));
            }
            mc.player.setPosition(newPos);
        }

        // Если не двигаемся, отправляем "на земле"
        if (movement.length() == 0 && bypass.get() != BypassType.None) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
        }
    }

    private void handleGrim() {
        // GrimAC требует маленьких смещений
        mc.player.getAbilities().flying = false;

        Vec3d movement = getMovementVector();
        if (movement.length() > 0) {
            Vec3d newPos = mc.player.getPos().add(movement.multiply(0.8));

            // Маленький подъём
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), mc.player.getY() + 0.001, mc.player.getZ(), false
            ));

            // Движение
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    newPos.x, newPos.y, newPos.z, false
            ));
            mc.player.setPosition(newPos);

            // Возврат на землю
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
        }

        // Периодически сбрасываем
        if (tickCounter % 10 == 0) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
        }
    }

    private void handleVulcan() {
        // Vulcan: креатив + пакеты
        mc.player.getAbilities().flying = true;
        mc.player.getAbilities().setFlySpeed(0.05f);

        // Отправляем маленькие смещения
        if (tickCounter % 2 == 0) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), mc.player.getY() - 0.0001, mc.player.getZ(), true
            ));
        }

        // Управление через скорость
        Vec3d movement = getMovementVector();
        if (movement.length() > 0) {
            mc.player.setVelocity(movement);
        }
    }

    private void handleMatrix() {
        // Matrix: ограничиваем количество пакетов
        mc.player.getAbilities().flying = false;

        Vec3d movement = getMovementVector();
        if (movement.length() > 0) {
            Vec3d newPos = mc.player.getPos().add(movement);
            int limit = Math.min(packetLimit.get(), 5);
            for (int i = 0; i < limit; i++) {
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        newPos.x, newPos.y, newPos.z, false
                ));
            }
            mc.player.setPosition(newPos);
        }
    }

    private void handleCustom() {
        // Пользовательский — комбинируем
        mc.player.getAbilities().flying = false;
        Vec3d movement = getMovementVector();
        if (movement.length() > 0) {
            Vec3d newPos = mc.player.getPos().add(movement);
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    newPos.x, newPos.y, newPos.z, false
            ));
            mc.player.setPosition(newPos);
        }
    }

    // --- ПОЛУЧЕНИЕ ВЕКТОРА ДВИЖЕНИЯ ---
    private Vec3d getMovementVector() {
        if (mc.player == null) return Vec3d.ZERO;

        double speedFactor = speed.get() / 10;
        Vec3d forward = Vec3d.ZERO;

        // Горизонталь
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

        // Вертикаль
        if (mc.options.jumpKey.isPressed()) {
            forward = forward.add(0, verticalSpeed.get() / 10, 0);
        }
        if (mc.options.sneakKey.isPressed()) {
            forward = forward.add(0, -verticalSpeed.get() / 10, 0);
        }

        return forward;
    }

    // --- ИНФОРМАЦИЯ ДЛЯ HUD ---
    @Override
    public String getInfoString() {
        return mode.get().toString() + " " + String.format("%.1f", speed.get());
    }
}
