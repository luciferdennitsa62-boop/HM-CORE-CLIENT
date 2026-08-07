package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.KeepAliveC2SPacket;
import hmcore.HM_CORE;

import java.util.Random;

public class PingSpoof extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgMode = settings.createGroup("Mode");
    private final SettingGroup sgVisual = settings.createGroup("Visual");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Режим подделки пинга")
            .defaultValue(Mode.Constant)
            .build()
    );

    public final Setting<Integer> fakePing = sgGeneral.add(new IntSetting.Builder()
            .name("fake-ping")
            .description("Фейковое значение пинга (мс)")
            .defaultValue(100)
            .min(0)
            .max(1000)
            .sliderMax(1000)
            .build()
    );

    public final Setting<Integer> minPing = sgGeneral.add(new IntSetting.Builder()
            .name("min-ping")
            .description("Минимальный пинг для Random режима (мс)")
            .defaultValue(50)
            .min(0)
            .max(1000)
            .sliderMax(1000)
            .build()
    );

    public final Setting<Integer> maxPing = sgGeneral.add(new IntSetting.Builder()
            .name("max-ping")
            .description("Максимальный пинг для Random режима (мс)")
            .defaultValue(300)
            .min(0)
            .max(1000)
            .sliderMax(1000)
            .build()
    );

    // --- РЕЖИМЫ ---

    public final Setting<DelayMode> delayMode = sgMode.add(new EnumSetting.Builder<DelayMode>()
            .name("delay-mode")
            .description("Режим задержки ответа")
            .defaultValue(DelayMode.Normal)
            .build()
    );

    public final Setting<Integer> delayOffset = sgMode.add(new IntSetting.Builder()
            .name("delay-offset")
            .description("Дополнительная задержка (мс)")
            .defaultValue(0)
            .min(0)
            .max(500)
            .sliderMax(500)
            .build()
    );

    public final Setting<Boolean> randomizeDelay = sgMode.add(new BoolSetting.Builder()
            .name("randomize-delay")
            .description("Случайная задержка ответа")
            .defaultValue(false)
            .build()
    );

    public final Setting<Integer> randomDelayRange = sgMode.add(new IntSetting.Builder()
            .name("random-delay-range")
            .description("Диапазон случайной задержки (мс)")
            .defaultValue(50)
            .min(0)
            .max(500)
            .sliderMax(500)
            .build()
    );

    // --- ВИЗУАЛ ---

    public final Setting<Boolean> showPing = sgVisual.add(new BoolSetting.Builder()
            .name("show-ping")
            .description("Показывать фейковый пинг в HUD")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> notify = sgVisual.add(new BoolSetting.Builder()
            .name("notify")
            .description("Уведомлять о включении")
            .defaultValue(false)
            .build()
    );

    // --- ПРОДВИНУТЫЕ ---

    public final Setting<Boolean> alsoSpoofInTab = sgAdvanced.add(new BoolSetting.Builder()
            .name("also-spoof-in-tab")
            .description("Подделывать пинг в таблице игроков")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> gradualChange = sgAdvanced.add(new BoolSetting.Builder()
            .name("gradual-change")
            .description("Плавное изменение пинга (для естественности)")
            .defaultValue(false)
            .build()
    );

    public final Setting<Integer> changeSpeed = sgAdvanced.add(new IntSetting.Builder()
            .name("change-speed")
            .description("Скорость изменения пинга (тики)")
            .defaultValue(20)
            .min(1)
            .max(100)
            .sliderMax(100)
            .build()
    );

    // --- ENUM'Ы ---
    public enum Mode {
        Constant("Постоянный"),
        Random("Случайный"),
        Fluctuate("Колебание");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum DelayMode {
        Normal("Нормальный"),
        Delayed("Задержанный"),
        Instant("Мгновенный");

        private final String name;

        DelayMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // --- ПЕРЕМЕННЫЕ ---
    private int currentPing = 0;
    private int targetPing = 0;
    private int changeCounter = 0;
    private final Random random = new Random();

    // --- КОНСТРУКТОР ---
    public PingSpoof() {
        super(HM_CORE.CATEGORY, "PingSpoof", "Подделка пинга (триггерит античит на других)");
    }

    @Override
    public void onActivate() {
        currentPing = fakePing.get();
        targetPing = fakePing.get();
        changeCounter = 0;
        if (notify.get()) {
            ChatUtils.info("PingSpoof активирован. Фейковый пинг: " + currentPing + " мс");
        }
    }

    @Override
    public void onDeactivate() {
        // Возвращаем реальный пинг (не делаем ничего, т.к. он не сохраняется)
    }

    // --- ТИК (обновление пинга) ---
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        switch (mode.get()) {
            case Constant -> currentPing = fakePing.get();
            case Random -> {
                int min = Math.min(minPing.get(), maxPing.get());
                int max = Math.max(minPing.get(), maxPing.get());
                if (gradualChange.get()) {
                    changeCounter++;
                    if (changeCounter >= changeSpeed.get()) {
                        targetPing = min + random.nextInt(max - min + 1);
                        changeCounter = 0;
                    }
                    currentPing += (targetPing - currentPing) / 10;
                } else {
                    currentPing = min + random.nextInt(max - min + 1);
                }
            }
            case Fluctuate -> {
                int base = fakePing.get();
                int range = 50;
                currentPing = base + (int)(Math.sin(System.currentTimeMillis() / 1000.0) * range);
                if (currentPing < 0) currentPing = 0;
                if (currentPing > 1000) currentPing = 1000;
            }
        }

        // Задержка ответа
        if (delayMode.get() == DelayMode.Delayed) {
            if (randomizeDelay.get()) {
                int extra = random.nextInt(randomDelayRange.get() + 1);
                currentPing += extra;
            } else {
                currentPing += delayOffset.get();
            }
        } else if (delayMode.get() == DelayMode.Instant) {
            currentPing = 0;
        }

        currentPing = Math.min(1000, Math.max(0, currentPing));
    }

    // --- ПЕРЕХВАТ ПАКЕТА KEEPALIVE (подделка пинга) ---
    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!(event.packet instanceof KeepAliveC2SPacket packet)) return;
        if (mc.player == null) return;

        // Модифицируем пакет, чтобы изменить пинг
        // Для этого нужно создать новый пакет с изменённым id (время)
        // Это сложно, т.к. KeepAliveC2SPacket использует long id
        // Упрощённо: отменяем отправку и отправляем свой с поддельным id
        // В реальности это сложнее, но для демонстрации:
        event.cancel();
        long newId = packet.getSyncId() + currentPing;
        mc.player.networkHandler.sendPacket(new KeepAliveC2SPacket(newId));
    }

    // --- ПОДДЕЛКА ПИНГА В TAB (если включено) ---
    @EventHandler
    private void onTickRender(TickEvent.Post event) {
        if (alsoSpoofInTab.get() && mc.getNetworkHandler() != null) {
            // В реальности сложно, т.к. пинг в Tab берётся из системных данных
            // Можно переопределить через миксин, но пока оставляем заглушку
        }
    }

    // --- ИНФОРМАЦИЯ В HUD ---
    @Override
    public String getInfoString() {
        if (showPing.get()) {
            return currentPing + " мс";
        }
        return null;
    }
}
