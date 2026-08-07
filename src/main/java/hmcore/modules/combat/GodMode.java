package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import hmcore.HM_CORE;

public class GodMode extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBypass = settings.createGroup("Bypass");
    private final SettingGroup sgVisual = settings.createGroup("Visual");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Режим неуязвимости")
            .defaultValue(Mode.Packet)
            .build()
    );

    public final Setting<Integer> healAmount = sgGeneral.add(new IntSetting.Builder()
            .name("heal-amount")
            .description("Количество восстанавливаемого здоровья (для режима Heal)")
            .defaultValue(4)
            .min(1)
            .max(20)
            .sliderMax(20)
            .build()
    );

    public final Setting<Integer> healDelay = sgGeneral.add(new IntSetting.Builder()
            .name("heal-delay")
            .description("Задержка между восстановлениями (тики)")
            .defaultValue(5)
            .min(1)
            .max(20)
            .sliderMax(20)
            .build()
    );

    public final Setting<Boolean> antiVoid = sgGeneral.add(new BoolSetting.Builder()
            .name("anti-void")
            .description("Защита от падения в пустоту")
            .defaultValue(true)
            .build()
    );

    // --- НАСТРОЙКИ ОБХОДА ---

    public final Setting<BypassType> bypass = sgBypass.add(new EnumSetting.Builder<BypassType>()
            .name("bypass")
            .description("Метод обхода античита")
            .defaultValue(BypassType.None)
            .build()
    );

    public final Setting<Boolean> fakeDamage = sgBypass.add(new BoolSetting.Builder()
            .name("fake-damage")
            .description("Отправлять фейковый урон серверу (чтобы не кикало)")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> silentHeal = sgBypass.add(new BoolSetting.Builder()
            .name("silent-heal")
            .description("Лечиться без анимации (незаметно)")
            .defaultValue(true)
            .build()
    );

    // --- ВИЗУАЛ ---

    public final Setting<Boolean> notify = sgVisual.add(new BoolSetting.Builder()
            .name("notify")
            .description("Уведомлять при активации")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> showHealth = sgVisual.add(new BoolSetting.Builder()
            .name("show-health")
            .description("Показывать здоровье в HUD")
            .defaultValue(true)
            .build()
    );

    // --- ENUM'Ы ---

    public enum Mode {
        Packet("Пакетный (не даёт умереть)"),
        Heal("Постоянное лечение"),
        Respawn("Мгновенное возрождение"),
        Super("Супер-режим");

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
        SpoofHealth("Подделка здоровья"),
        Full("Полный обход")
    }

    // --- ПЕРЕМЕННЫЕ ---
    private int healCounter = 0;
    private float lastHealth = 20.0f;
    private boolean isActive = false;

    // --- КОНСТРУКТОР ---
    public GodMode() {
        super(HM_CORE.CATEGORY, "GodMode", "Режим неуязвимости (эксплойт)");
    }

    @Override
    public void onActivate() {
        healCounter = 0;
        isActive = false;
        if (mc.player != null) {
            lastHealth = mc.player.getHealth();
        }
        if (notify.get()) {
            ChatUtils.info("GodMode включен. Режим: " + mode.get());
        }
    }

    @Override
    public void onDeactivate() {
        isActive = false;
    }

    // --- ТИК ---
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        healCounter++;

        switch (mode.get()) {
            case Packet -> handlePacket();
            case Heal -> handleHeal();
            case Respawn -> handleRespawn();
            case Super -> handleSuper();
        }

        // Анти-void
        if (antiVoid.get() && mc.player.getY() < -60) {
            mc.player.setPosition(mc.player.getX(), 80, mc.player.getZ());
            if (notify.get()) {
                ChatUtils.warn("Спасение от пустоты!");
            }
        }

        // Подделка здоровья (обход)
        if (bypass.get() == BypassType.SpoofHealth && mc.player.age % 5 == 0) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
        }
    }

    // --- ОБРАБОТКА УРОНА (перехват пакетов) ---
    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null) return;

        if (event.packet instanceof HealthUpdateS2CPacket healthPacket) {
            float health = healthPacket.getHealth();
            float food = healthPacket.getFood();

            // Если здоровье упало, пытаемся восстановить
            if (mode.get() == Mode.Packet && health < 20.0f) {
                // Отправляем фейковый пакет здоровья
                mc.player.setHealth(20.0f);
                event.cancel();
                if (notify.get() && mc.player.age % 20 == 0) {
                    ChatUtils.info("§aБлокирован урон! Здоровье восстановлено.");
                }
            }

            lastHealth = health;
        }
    }

    // --- РЕЖИМ PACKET ---
    private void handlePacket() {
        if (mc.player.getHealth() < 20.0f && mc.player.age % 2 == 0) {
            mc.player.setHealth(20.0f);
            if (fakeDamage.get()) {
                // Отправляем фейковый урон серверу, чтобы он не кикнул
                mc.player.damage(DamageSource.GENERIC, 0.1f);
            }
            isActive = true;
        }
    }

    // --- РЕЖИМ HEAL ---
    private void handleHeal() {
        if (healCounter >= healDelay.get()) {
            if (mc.player.getHealth() < 20.0f) {
                float newHealth = Math.min(mc.player.getHealth() + healAmount.get(), 20.0f);
                mc.player.setHealth(newHealth);
                if (notify.get() && healCounter % 10 == 0) {
                    ChatUtils.info("§aВосстановлено здоровье: " + String.format("%.1f", newHealth));
                }
            }
            healCounter = 0;
        }
    }

    // --- РЕЖИМ RESPAWN ---
    private void handleRespawn() {
        if (mc.player.getHealth() <= 0.0f) {
            mc.player.setHealth(20.0f);
            mc.player.fallDistance = 0;
            mc.player.setPosition(mc.player.getX(), mc.player.getY() + 0.1, mc.player.getZ());
            if (notify.get()) {
                ChatUtils.warn("§eМгновенное возрождение!");
            }
        }
    }

    // --- СУПЕР-РЕЖИМ ---
    private void handleSuper() {
        // Комбинация всех режимов
        handlePacket();
        handleHeal();
        handleRespawn();

        // Дополнительно: постоянная отправка пакетов "на земле"
        if (mc.player.age % 3 == 0) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
        }

        // Отключение падения
        mc.player.fallDistance = 0;
    }

    @Override
    public String getInfoString() {
        if (mc.player != null) {
            return String.format("%.1f", mc.player.getHealth()) + "/20";
        }
        return "0/20";
    }
}
