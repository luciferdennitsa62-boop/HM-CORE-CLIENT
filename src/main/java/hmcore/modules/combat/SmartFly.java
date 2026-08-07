package hmcore.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;
import hmcore.HM_CORE;

public class SmartFly extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBypass = settings.createGroup("Bypass");
    private final SettingGroup sgVisual = settings.createGroup("Visual");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Режим полета")
            .defaultValue(Mode.Vanilla)
            .build()
    );

    public final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
            .name("speed")
            .description("Скорость полета (горизонтальная)")
            .defaultValue(1.0)
            .min(0.1)
            .max(10.0)
            .sliderMax(10.0)
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
            .description("Предотвращает кик за полет")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> glide = sgGeneral.add(new BoolSetting.Builder()
            .name("glide")
            .description("Плавное снижение вместо падения")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> noClip = sgGeneral.add(new BoolSetting.Builder()
            .name("no-clip")
            .description("Проходить сквозь стены (может банить)")
            .defaultValue(false)
            .build()
    );

    // --- НАСТРОЙКИ ОБХОДА ---

    public final Setting<BypassMode> bypass = sgBypass.add(new EnumSetting.Builder<BypassMode>()
            .name("bypass")
            .description("Метод обхода античита")
            .defaultValue(BypassMode.None)
            .build()
    );

    public final Setting<Integer> packetLimit = sgBypass.add(new IntSetting.Builder()
            .name("packet-limit")
            .description("Максимальное количество пакетов за тик (для Packet режима)")
            .defaultValue(5)
            .min(1)
            .max(20)
            .sliderMax(20)
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

    public final Setting<Boolean> antiStuck = sgBypass.add(new BoolSetting.Builder()
            .name("anti-stuck")
            .description("Автоматически выходить из блоков")
            .defaultValue(true)
            .build()
    );

    // --- ВИЗУАЛЬНЫЕ НАСТРОЙКИ ---

    public final Setting<Boolean> renderSpeed = sgVisual.add(new BoolSetting.Builder()
            .name("render-speed")
            .description("Показывать скорость в HUD")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> trailParticles = sgVisual.add(new BoolSetting.Builder()
            .name("trail-particles")
            .description("Оставлять след из частиц")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> notifyToggle = sgVisual.add(new BoolSetting.Builder()
            .name("notify-toggle")
            .description("Уведомлять при включении/выключении")
            .defaultValue(true)
            .build()
    );

    // --- ПРОДВИНУТЫЕ НАСТРОЙКИ ---

    public final Setting<Boolean> smartDirection = sgAdvanced.add(new BoolSetting.Builder()
            .name("smart-direction")
            .description("Учитывать направление взгляда для движения")
            .defaultValue(true)
            .build()
    );

    public final Setting<Integer> autoDisableTime = sgAdvanced.add(new IntSetting.Builder()
            .name("auto-disable-time")
            .description("Автоматически выключать через X секунд (0 = выкл)")
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

    // --- ПЕРЕМЕННЫЕ ---

    private long startTime = 0;
    private int tickCounter = 0;

    // --- ENUM'Ы ---

    public enum Mode {
        Vanilla("Ванильный (креатив)"),
        Packet("Пакетный"),
        Grim("GrimAC"),
        Matrix("Matrix"),
        Vulcan("Vulcan"),
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

    public enum BypassMode {
        None("Выкл"),
        AntiKick("Анти-кик"),
        PacketSpoof("Подделка пакетов"),
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

    // --- КОНСТРУКТОР ---

    public SmartFly() {
        super(HM_CORE.CATEGORY, "SmartFly", "Умный полет с обходом античитов и кучей настроек");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;
        startTime = System.currentTimeMillis();

        // Для Vanilla режима включаем креативный полет
        if (mode.get() == Mode.Vanilla) {
            mc.player.getAbilities().flying = true;
            mc.player.getAbilities().setFlySpeed(speed.get().floatValue() / 10f);
        }

        if (notifyToggle.get()) {
            ChatUtils.info("SmartFly включен. Режим: " + mode.get());
        }
    }

    @Override
    public void onDeactivate() {
        if (mc.player == null) return;

        // Отключаем креативный полет
        mc.player.getAbilities().flying = false;
        mc.player.getAbilities().setFlySpeed(0.05f);

        // Возвращаем нормальную гравитацию
        mc.player.setVelocity(mc.player.getVelocity().x, -0.5, mc.player.getVelocity().z);

        if (notifyToggle.get()) {
            ChatUtils.info("SmartFly выключен.");
        }
    }

    // --- ОСНОВНОЙ ТИК ---

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Автовыключение по времени
        if (autoDisableTime.get() > 0) {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            if (elapsed > autoDisableTime.get()) {
                toggle();
                ChatUtils.warn("SmartFly автоматически выключен (таймер)");
                return;
            }
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
            case Matrix -> handleMatrix();
            case Vulcan -> handleVulcan();
            case Custom -> handleCustom();
        }

        // Анти-кик
        if (antiKick.get() && mc.player.age % 20 == 0) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
        }

        tickCounter++;
    }

    // --- ОБРАБОТЧИКИ РЕЖИМОВ ---

    private void handleVanilla() {
        mc.player.getAbilities().flying = true;
        mc.player.getAbilities().setFlySpeed(speed.get().floatValue() / 10f);

        // Вертикальное управление
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
            for (int i = 0; i < packetLimit.get(); i++) {
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        newPos.x, newPos.y, newPos.z, false
                ));
            }
            mc.player.setPosition(newPos);
        }

        if (forward.length() == 0 && bypass.get() != BypassMode.None) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
        }
    }

    private void handleGrim() {
        mc.player.getAbilities().flying = false;

        Vec3d forward = getMovementVector();
        if (forward.length() > 0) {
            Vec3d newPos = mc.player.getPos().add(forward.multiply(0.8));
            mc.player.setPosition(newPos);
        }

        if (mc.player.age % 5 == 0) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
        }

        if (glide.get()) {
            mc.player.setVelocity(mc.player.getVelocity().x, -0.01, mc.player.getVelocity().z);
        }
    }

    private void handleMatrix() {
        mc.player.getAbilities().flying = false;

        Vec3d forward = getMovementVector();
        if (forward.length() > 0) {
            Vec3d newPos = mc.player.getPos().add(forward);
            for (int i = 0; i < Math.min(packetLimit.get(), 5); i++) {
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        newPos.x, newPos.y, newPos.z, false
                ));
            }
            mc.player.setPosition(newPos);
        }
    }

    private void handleVulcan() {
        mc.player.getAbilities().flying = true;
        mc.player.getAbilities().setFlySpeed(0.05f);

        if (mc.player.age % 2 == 0) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), mc.player.getY() - 0.0001, mc.player.getZ(), true
            ));
        }

        // Дополнительный обход через прыжок
        if (mc.options.jumpKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, verticalSpeed.get() / 2, mc.player.getVelocity().z);
        }
    }

    private void handleCustom() {
        // Пользовательские настройки — можно добавить свои комбинации
        mc.player.getAbilities().flying = false;
        Vec3d forward = getMovementVector();
        if (forward.length() > 0) {
            Vec3d newPos = mc.player.getPos().add(forward);
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    newPos.x, newPos.y, newPos.z, false
            ));
            mc.player.setPosition(newPos);
        }
    }

    // --- ВСПОМОГАТЕЛЬНЫЙ МЕТОД ДЛЯ ДВИЖЕНИЯ ---

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

    // --- ВИЗУАЛИЗАЦИЯ ---

    @EventHandler
    private void onTickRender(TickEvent.Post event) {
        if (renderSpeed.get() && isActive() && mc.player != null) {
            // Здесь можно добавить отображение скорости в HUD
            // В Meteor это делается через HudElement, но пока оставим заглушку
        }
    }
}
