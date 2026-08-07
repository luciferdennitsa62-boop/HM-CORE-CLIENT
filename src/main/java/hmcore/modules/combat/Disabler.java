package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import hmcore.HM_CORE;

import java.util.Random;

public class Disabler extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBypass = settings.createGroup("Bypass");
    private final SettingGroup sgVisual = settings.createGroup("Visual");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Режим отключения античита")
            .defaultValue(Mode.Motion)
            .build()
    );

    public final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
            .name("delay")
            .description("Задержка между отправкой пакетов (тики)")
            .defaultValue(5)
            .min(1)
            .max(20)
            .sliderMax(20)
            .build()
    );

    public final Setting<Integer> packetCount = sgGeneral.add(new IntSetting.Builder()
            .name("packet-count")
            .description("Количество пакетов за раз")
            .defaultValue(3)
            .min(1)
            .max(10)
            .sliderMax(10)
            .build()
    );

    // --- НАСТРОЙКИ ОБХОДА ---

    public final Setting<BypassType> bypass = sgBypass.add(new EnumSetting.Builder<BypassType>()
            .name("bypass")
            .description("Дополнительный метод обхода")
            .defaultValue(BypassType.None)
            .build()
    );

    public final Setting<Boolean> antiKick = sgBypass.add(new BoolSetting.Builder()
            .name("anti-kick")
            .description("Защита от кика")
            .defaultValue(true)
            .build()
    );

    public final Setting<Double> motionY = sgBypass.add(new DoubleSetting.Builder()
            .name("motion-y")
            .description("Вертикальное смещение для Motion режима")
            .defaultValue(0.1)
            .min(0.01)
            .max(1.0)
            .sliderMax(1.0)
            .build()
    );

    // --- ВИЗУАЛ ---

    public final Setting<Boolean> notify = sgVisual.add(new BoolSetting.Builder()
            .name("notify")
            .description("Уведомлять об активации")
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

    public final Setting<Boolean> autoDisable = sgAdvanced.add(new BoolSetting.Builder()
            .name("auto-disable")
            .description("Автоматически выключить через 30 секунд (безопасность)")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> randomize = sgAdvanced.add(new BoolSetting.Builder()
            .name("randomize")
            .description("Случайные значения для пакетов (труднее детектить)")
            .defaultValue(true)
            .build()
    );

    // --- ENUM'Ы ---

    public enum Mode {
        Motion("Движение (Motion)"),
        ClientCommand("Клиентские команды"),
        Trident("Трезубец (Trident)"),
        Packet("Пакетный"),
        Vulcan("Vulcan"),
        Grim("GrimAC"),
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
        PacketSpoof("Подделка пакетов"),
        Full("Полный обход")
    }

    // --- ПЕРЕМЕННЫЕ ---
    private int tickCounter = 0;
    private long startTime = 0;
    private final Random random = new Random();

    // --- КОНСТРУКТОР ---
    public Disabler() {
        super(HM_CORE.CATEGORY, "Disabler", "Пытается отключить или обойти античит на сервере");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        startTime = System.currentTimeMillis();
        if (notify.get()) {
            ChatUtils.info("Disabler включен. Режим: " + mode.get());
        }
    }

    @Override
    public void onDeactivate() {
        if (notify.get()) {
            ChatUtils.info("Disabler выключен.");
        }
    }

    // --- ТИК ---
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Автовыключение
        if (autoDisable.get() && System.currentTimeMillis() - startTime > 30000) {
            toggle();
            ChatUtils.warn("Disabler автоматически выключен (30 сек)");
            return;
        }

        tickCounter++;
        if (tickCounter < delay.get()) return;
        tickCounter = 0;

        // Обработка режимов
        switch (mode.get()) {
            case Motion -> handleMotion();
            case ClientCommand -> handleClientCommand();
            case Trident -> handleTrident();
            case Packet -> handlePacket();
            case Vulcan -> handleVulcan();
            case Grim -> handleGrim();
            case Matrix -> handleMatrix();
        }

        // Дополнительный обход
        if (bypass.get() == BypassType.PacketSpoof && mc.player.age % 3 == 0) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
        }

        // Анти-кик
        if (antiKick.get() && mc.player.age % 20 == 0) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
        }
    }

    // --- ОБРАБОТЧИКИ ---

    private void handleMotion() {
        // Отправляем пакеты с изменённой позицией
        double y = mc.player.getY();
        double offset = randomize.get() ?
                motionY.get() + (random.nextDouble() - 0.5) * 0.01 :
                motionY.get();

        for (int i = 0; i < packetCount.get(); i++) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), y + offset, mc.player.getZ(), false
            ));
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), y, mc.player.getZ(), true
            ));
        }
    }

    private void handleClientCommand() {
        // Отправляем клиентские команды
        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(
                mc.player, ClientCommandC2SPacket.Mode.REQUEST_STATS
        ));

        if (packetCount.get() > 1) {
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(
                    mc.player, ClientCommandC2SPacket.Mode.PERFORM_RESPAWN
            ));
        }
    }

    private void handleTrident() {
        // Имитация использования трезубца
        if (mc.player.age % 10 == 0) {
            mc.player.setVelocity(
                    randomize.get() ? (random.nextDouble() - 0.5) * 0.1 : 0,
                    0.1,
                    randomize.get() ? (random.nextDouble() - 0.5) * 0.1 : 0
            );
        }

        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(false));
    }

    private void handlePacket() {
        // Отправка большого количества пакетов
        for (int i = 0; i < packetCount.get(); i++) {
            mc.player.networkHandler.sendPacket(new KeepAliveC2SPacket(i));
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(i % 9));
        }

        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(
                mc.player.age % 2 == 0
        ));
    }

    private void handleVulcan() {
        // Vulcan: комбинация пакетов движения и команд
        double y = mc.player.getY();

        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), y + 0.0001, mc.player.getZ(), false
        ));

        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), y - 0.0001, mc.player.getZ(), true
        ));

        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(
                mc.player, ClientCommandC2SPacket.Mode.REQUEST_STATS
        ));
    }

    private void handleGrim() {
        // Grim: специфические значения
        double y = mc.player.getY();

        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), y + 0.001091981, mc.player.getZ(), false
        ));

        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), y + 0.000114514, mc.player.getZ(), true
        ));

        if (packetCount.get() > 2) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), y, mc.player.getZ(), true
            ));
        }
    }

    private void handleMatrix() {
        // Matrix: много пакетов с маленьким смещением
        double y = mc.player.getY();

        for (int i = 0; i < packetCount.get(); i++) {
            double offset = randomize.get() ?
                    0.0001 + (random.nextDouble() - 0.5) * 0.00005 :
                    0.0001;
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), y + offset * i, mc.player.getZ(), false
            ));
        }

        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), y, mc.player.getZ(), true
        ));
    }

    // --- ОБРАБОТКА ВХОДЯЩИХ ПАКЕТОВ (для блокировки киков) ---
    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        // Можно перехватывать пакеты кика и отменять их
        // Но это сложно и может вызвать проблемы
        // Пока оставляем как заглушку
    }

    @Override
    public String getInfoString() {
        if (showStatus.get()) {
            return mode.get().toString();
        }
        return null;
    }
}
