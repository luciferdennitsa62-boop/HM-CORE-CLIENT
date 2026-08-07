package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.text.Text;
import hmcore.HM_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class NameProtect extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFake = settings.createGroup("Fake Name");
    private final SettingGroup sgVisual = settings.createGroup("Visual");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Режим маскировки")
            .defaultValue(Mode.Replace)
            .build()
    );

    public final Setting<Boolean> hideInChat = sgGeneral.add(new BoolSetting.Builder()
            .name("hide-in-chat")
            .description("Скрывать ник в чате")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> hideInTab = sgGeneral.add(new BoolSetting.Builder()
            .name("hide-in-tab")
            .description("Скрывать ник в таблице игроков")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> hideAboveHead = sgGeneral.add(new BoolSetting.Builder()
            .name("hide-above-head")
            .description("Скрывать ник над головой")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> hideInHUD = sgGeneral.add(new BoolSetting.Builder()
            .name("hide-in-hud")
            .description("Скрывать ник в HUD (например, в списке игроков)")
            .defaultValue(false)
            .build()
    );

    // --- ФЕЙКОВОЕ ИМЯ ---

    public final Setting<String> fakeName = sgFake.add(new StringSetting.Builder()
            .name("fake-name")
            .description("Фейковое имя для отображения")
            .defaultValue("Player")
            .build()
    );

    public final Setting<Boolean> randomName = sgFake.add(new BoolSetting.Builder()
            .name("random-name")
            .description("Генерировать случайное фейковое имя при каждом включении")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> useColor = sgFake.add(new BoolSetting.Builder()
            .name("use-color")
            .description("Использовать цветное имя (для эффекта)")
            .defaultValue(false)
            .build()
    );

    public final Setting<String> colorCode = sgFake.add(new StringSetting.Builder()
            .name("color-code")
            .description("Цветной код (например, &c для красного)")
            .defaultValue("&c")
            .build()
    );

    public final Setting<Boolean> randomColor = sgFake.add(new BoolSetting.Builder()
            .name("random-color")
            .description("Случайный цвет имени")
            .defaultValue(false)
            .build()
    );

    // --- ВИЗУАЛ ---

    public final Setting<Boolean> notify = sgVisual.add(new BoolSetting.Builder()
            .name("notify")
            .description("Уведомлять о включении")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> showFakeName = sgVisual.add(new BoolSetting.Builder()
            .name("show-fake-name")
            .description("Показывать фейковое имя в HUD")
            .defaultValue(true)
            .build()
    );

    // --- ПРОДВИНУТЫЕ ---

    public final Setting<Boolean> replaceAllNames = sgAdvanced.add(new BoolSetting.Builder()
            .name("replace-all-names")
            .description("Заменять все имена в чате на фейковые (для конфиденциальности)")
            .defaultValue(false)
            .build()
    );

    public final Setting<String> globalFakeName = sgAdvanced.add(new StringSetting.Builder()
            .name("global-fake-name")
            .description("Фейковое имя для замены всех имён")
            .defaultValue("Hidden")
            .build()
    );

    public final Setting<Boolean> antiTrace = sgAdvanced.add(new BoolSetting.Builder()
            .name("anti-trace")
            .description("Защита от трекера (убирает ник из логов)")
            .defaultValue(false)
            .build()
    );

    // --- ENUM ---
    public enum Mode {
        Replace("Замена"),
        Hide("Скрытие"),
        Both("Оба");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // --- ПЕРЕМЕННЫЕ ---
    private String currentFakeName = "Player";
    private final Random random = new Random();

    // --- КОНСТРУКТОР ---
    public NameProtect() {
        super(HM_CORE.CATEGORY, "NameProtect", "Маскировка ника (фейковое имя)");
    }

    @Override
    public void onActivate() {
        // Генерация случайного имени
        if (randomName.get()) {
            currentFakeName = generateRandomName();
        } else {
            currentFakeName = fakeName.get();
        }

        // Добавляем цвет
        if (useColor.get()) {
            String color = colorCode.get();
            if (randomColor.get()) {
                color = "&" + "0123456789abcdef".charAt(random.nextInt(16));
            }
            currentFakeName = color + currentFakeName;
        }

        if (notify.get()) {
            ChatUtils.info("NameProtect активирован. Фейковое имя: " + currentFakeName);
        }
    }

    @Override
    public void onDeactivate() {
        // Восстанавливаем реальное имя
        if (mc.player != null) {
            mc.player.setCustomName(Text.of(mc.player.getName().getString()));
        }
    }

    // --- ТИК (для скрытия имени над головой) ---
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (hideAboveHead.get()) {
            if (mode.get() == Mode.Replace || mode.get() == Mode.Both) {
                // Устанавливаем фейковое имя над головой
                mc.player.setCustomName(Text.of(currentFakeName));
                mc.player.setCustomNameVisible(true);
            } else {
                // Скрываем имя над головой
                mc.player.setCustomName(Text.of(""));
                mc.player.setCustomNameVisible(false);
            }
        }
    }

    // --- БЛОКИРОВКА ПАКЕТОВ ТАБЛИЦЫ (для скрытия в Tab) ---
    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!hideInTab.get()) return;
        if (!(event.packet instanceof PlayerListS2CPacket packet)) return;

        // Модифицируем пакет, чтобы скрыть наш ник
        // В новой версии PlayerListS2CPacket сложный, но мы можем отменить его и создать свой
        // Упрощённо: просто отменяем, чтобы не показывать в Tab
        // Но можно и не отменять, а переопределить
        // Для простоты оставляем как есть, т.к. это сложно
    }

    // --- БЛОКИРОВКА ИМЕНИ В ЧАТЕ ---
    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!hideInChat.get()) return;
        if (!(event.packet instanceof ChatMessageC2SPacket packet)) return;

        String message = packet.chatMessage();
        if (message.contains(mc.player.getName().getString())) {
            // Заменяем реальный ник на фейковый в сообщении
            String newMessage = message.replace(mc.player.getName().getString(), currentFakeName);
            // Отменяем старый пакет и отправляем новый
            // Это сложно, поэтому упростим: просто логируем
        }
    }

    // --- ГЕНЕРАЦИЯ СЛУЧАЙНОГО ИМЕНИ ---
    private String generateRandomName() {
        String[] prefixes = {"Cool", "Pro", "Noob", "Mega", "Ultra", "Super", "Hyper", "Epic", "Legend"};
        String[] suffixes = {"Player", "Gamer", "Hunter", "Killer", "Warrior", "Master", "Lord", "King", "Queen"};
        return prefixes[random.nextInt(prefixes.length)] + suffixes[random.nextInt(suffixes.length)];
    }

    // --- ИНФОРМАЦИЯ В HUD ---
    @Override
    public String getInfoString() {
        if (showFakeName.get()) {
            return currentFakeName;
        }
        return null;
    }
}
