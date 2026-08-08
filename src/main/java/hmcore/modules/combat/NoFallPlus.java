package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import hmcore.HM_CORE;

public class NoFallPlus extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBypass = settings.createGroup("Bypass");
    private final SettingGroup sgVisual = settings.createGroup("Visual");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Режим защиты от падения")
            .defaultValue(Mode.Packet)
            .build()
    );

    public final Setting<Double> fallDistance = sgGeneral.add(new DoubleSetting.Builder()
            .name("fall-distance")
            .description("Максимальная дистанция падения, после которой включается защита")
            .defaultValue(3.0)
            .min(1.0)
            .max(10.0)
            .sliderMax(10.0)
            .build()
    );

    public final Setting<Boolean> antiKick = sgGeneral.add(new BoolSetting.Builder()
            .name("anti-kick")
            .description("Защита от кика за неправильное движение")
            .defaultValue(true)
            .build()
    );

    // --- НАСТРОЙКИ ОБХОДА ---

    public final Setting<BypassType> bypass = sgBypass.add(new EnumSetting.Builder<BypassType>()
            .name("bypass")
            .description("Дополнительный метод обхода античита")
            .defaultValue(BypassType.None)
            .build()
    );

    public final Setting<Integer> packetCount = sgBypass.add(new IntSetting.Builder()
            .name("packet-count")
            .description("Количество пакетов для отправки (для Packet режима)")
            .defaultValue(3)
            .min(1)
            .max(10)
            .sliderMax(10)
            .build()
    );

    public final Setting<Double> offsetY = sgBypass.add(new DoubleSetting.Builder()
            .name("offset-y")
            .description("Смещение по Y для обмана античита")
            .defaultValue(0.01)
            .min(0.001)
            .max(0.1)
            .sliderMax(0.1)
            .build()
    );

    // --- ВИЗУАЛ ---

    public final Setting<Boolean> notify = sgVisual.add(new BoolSetting.Builder()
            .name("notify")
            .description("Уведомлять о срабатывании защиты")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> showFallDistance = sgVisual.add(new BoolSetting.Builder()
            .name("show-fall-distance")
            .description("Показывать текущую дистанцию падения в HUD")
            .defaultValue(true)
            .build()
    );

    // --- ENUM'Ы ---

    public enum Mode {
        Vanilla("Ванильный (простой)"),
        Packet("Пакетный (обход)"),
        Grim("GrimAC"),
        Vulcan("Vulcan"),
        Matrix("Matrix");

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
        PacketSpoof("Подделка пакетов")
    }

    // --- ПЕРЕМЕННЫЕ ---
    private boolean hasTriggered = false;
    private int tickCounter = 0;

    // --- КОНСТРУКТОР ---
    public NoFallPlus() {
        super(HM_CORE.CATEGORY, "NoFall+", "Улучшенная защита от падения с обходом античитов");
    }

    @Override
    public void onActivate() {
        hasTriggered = false;
        tickCounter = 0;
        if (notify.get()) {
            ChatUtils.info("NoFall+ включен. Режим: " + mode.get());
        }
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) {
            mc.player.fallDistance = 0;
        }
        hasTriggered = false;
    }

    // --- ТИК ---
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        tickCounter++;

        // Если падение маленькое — ничего не делаем
        if (mc.player.fallDistance < fallDistance.get()) return;

        // Обработка режимов
        switch (mode.get()) {
            case Vanilla -> handleVanilla();
            case Packet -> handlePacket();
            case Grim -> handleGrim();
            case Vulcan -> handleVulcan();
            case Matrix -> handleMatrix();
        }

        // Дополнительный обход
        if (bypass.get() == BypassType.PacketSpoof && tickCounter % 5 == 0) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
        }

        // Сброс счётчика
        if (mc.player.isOnGround()) {
            mc.player.fallDistance = 0;
            hasTriggered = false;
        }
    }

    // --- ОБРАБОТЧИКИ ---

    private void handleVanilla() {
        // Простой способ: сбрасываем fallDistance
        if (mc.player.fallDistance > fallDistance.get()) {
            mc.player.fallDistance = 0;
            if (notify.get() && !hasTriggered) {
                ChatUtils.info("§aСброс падения!");
                hasTriggered = true;
            }
        }
    }

    private void handlePacket() {
        // Отправляем пакет "на земле" прямо перед ударом
        if (mc.player.fallDistance > fallDistance.get()) {
            for (int i = 0; i < packetCount.get(); i++) {
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
            }
            mc.player.fallDistance = 0;
            if (notify.get() && !hasTriggered) {
                ChatUtils.info("§aПакетный обход падения!");
                hasTriggered = true;
            }
        }
    }

    private void handleGrim() {
        // Grim: специфические значения
        if (mc.player.fallDistance > fallDistance.get()) {
            double y = mc.player.getY();

            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), y + 0.001091981, mc.player.getZ(), false
            ));
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), y + 0.000114514, mc.player.getZ(), true
            ));
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));

            mc.player.fallDistance = 0;
            if (notify.get() && !hasTriggered) {
                ChatUtils.info("§aGrim обход падения!");
                hasTriggered = true;
            }
        }
    }

    private void handleVulcan() {
        // Vulcan: отправляем пакет с небольшим смещением
        if (mc.player.fallDistance > fallDistance.get()) {
            double y = mc.player.getY();

            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), y - 0.0001, mc.player.getZ(), true
            ));
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), y + 0.0001, mc.player.getZ(), false
            ));
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));

            mc.player.fallDistance = 0;
            if (notify.get() && !hasTriggered) {
                ChatUtils.info("§aVulcan обход падения!");
                hasTriggered = true;
            }
        }
    }

    private void handleMatrix() {
        // Matrix: несколько пакетов с изменённой позицией
        if (mc.player.fallDistance > fallDistance.get()) {
            double y = mc.player.getY();

            for (int i = 0; i < packetCount.get(); i++) {
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        mc.player.getX(), y + offsetY.get() * i, mc.player.getZ(), false
                ));
            }
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));

            mc.player.fallDistance = 0;
            if (notify.get() && !hasTriggered) {
                ChatUtils.info("§aMatrix обход падения!");
                hasTriggered = true;
            }
        }
    }

    @Override
    public String getInfoString() {
        if (showFallDistance.get() && mc.player != null) {
            return String.format("%.1f", mc.player.fallDistance) + " блоков";
        }
        return null;
    }
}
