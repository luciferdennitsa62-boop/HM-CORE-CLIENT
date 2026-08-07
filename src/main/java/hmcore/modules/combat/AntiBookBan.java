package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.packet.c2s.play.BookUpdateC2SPacket;
import net.minecraft.text.Text;
import hmcore.HM_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class AntiBookBan extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilter = settings.createGroup("Filter");
    private final SettingGroup sgVisual = settings.createGroup("Visual");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Режим защиты")
            .defaultValue(Mode.Block)
            .build()
    );

    public final Setting<Boolean> blockPacket = sgGeneral.add(new BoolSetting.Builder()
            .name("block-packet")
            .description("Блокировать отправку пакета книги на сервер")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> autoRemove = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-remove")
            .description("Автоматически удалять книгу из инвентаря")
            .defaultValue(false)
            .build()
    );

    // --- ФИЛЬТРЫ ---

    public final Setting<Boolean> filterColorCodes = sgFilter.add(new BoolSetting.Builder()
            .name("filter-color-codes")
            .description("Удалять книги с цветными кодами (§)")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> filterIllegalCharacters = sgFilter.add(new BoolSetting.Builder()
            .name("filter-illegal-characters")
            .description("Удалять книги с недопустимыми символами")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> filterTooManyPages = sgFilter.add(new BoolSetting.Builder()
            .name("filter-too-many-pages")
            .description("Удалять книги с подозрительно большим числом страниц")
            .defaultValue(true)
            .build()
    );

    public final Setting<Integer> maxPages = sgFilter.add(new IntSetting.Builder()
            .name("max-pages")
            .description("Максимальное количество страниц в книге")
            .defaultValue(10)
            .min(1)
            .max(100)
            .sliderMax(100)
            .build()
    );

    public final Setting<Integer> maxPageLength = sgFilter.add(new IntSetting.Builder()
            .name("max-page-length")
            .description("Максимальное количество символов на странице")
            .defaultValue(1000)
            .min(100)
            .max(10000)
            .sliderMax(10000)
            .build()
    );

    public final Setting<Boolean> filterJson = sgFilter.add(new BoolSetting.Builder()
            .name("filter-json")
            .description("Удалять книги с JSON-структурами")
            .defaultValue(true)
            .build()
    );

    public final Setting<List<String>> blacklistedWords = sgFilter.add(new StringListSetting.Builder()
            .name("blacklisted-words")
            .description("Список запрещённых слов (через запятую)")
            .defaultValue("kick,ban,crash,op,admin,console,stop,restart")
            .build()
    );

    // --- ВИЗУАЛ ---

    public final Setting<Boolean> notify = sgVisual.add(new BoolSetting.Builder()
            .name("notify")
            .description("Уведомлять о блокировке книги")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> logToChat = sgVisual.add(new BoolSetting.Builder()
            .name("log-to-chat")
            .description("Показывать подробную информацию о заблокированной книге в чат")
            .defaultValue(false)
            .build()
    );

    // --- ПРОДВИНУТЫЕ ---

    public final Setting<Boolean> scanInventory = sgAdvanced.add(new BoolSetting.Builder()
            .name("scan-inventory")
            .description("Сканировать инвентарь на наличие бан-книг")
            .defaultValue(true)
            .build()
    );

    public final Setting<Integer> scanDelay = sgAdvanced.add(new IntSetting.Builder()
            .name("scan-delay")
            .description("Задержка между сканированиями (тики)")
            .defaultValue(20)
            .min(1)
            .max(100)
            .sliderMax(100)
            .build()
    );

    // --- ENUM ---
    public enum Mode {
        Block("Блокировка"),
        Remove("Удаление"),
        Both("Блокировка и удаление");

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
    private int scanCounter = 0;

    // --- КОНСТРУКТОР ---
    public AntiBookBan() {
        super(HM_CORE.CATEGORY, "AntiBookBan", "Защита от бан-книг и вредоносных книг");
    }

    @Override
    public void onActivate() {
        scanCounter = 0;
        if (notify.get()) {
            ChatUtils.info("AntiBookBan активирован. Режим: " + mode.get());
        }
    }

    @Override
    public void onDeactivate() {
        // Ничего
    }

    // --- ОБРАБОТКА ПАКЕТА КНИГИ ---
    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!(event.packet instanceof BookUpdateC2SPacket packet)) return;
        if (mc.player == null) return;

        ItemStack book = packet.getBook();
        if (book.getItem() != Items.WRITTEN_BOOK && book.getItem() != Items.WRITABLE_BOOK) return;

        // Проверка книги на опасность
        BookCheckResult result = checkBook(book);

        if (result.isDangerous()) {
            // Блокируем пакет
            if (blockPacket.get()) {
                event.cancel();
                if (notify.get()) {
                    ChatUtils.warn("§cЗаблокирована отправка опасной книги на сервер!");
                }
                if (logToChat.get()) {
                    ChatUtils.info("§7Причина: " + result.getReason());
                }
            }

            // Удаляем книгу
            if (autoRemove.get() || mode.get() == Mode.Remove || mode.get() == Mode.Both) {
                removeBook(book);
                if (notify.get()) {
                    ChatUtils.info("§aОпасная книга удалена из инвентаря!");
                }
            }
        }
    }

    // --- ПРОВЕРКА КНИГИ ---
    private BookCheckResult checkBook(ItemStack book) {
        if (book.isEmpty() || book.getItem() != Items.WRITTEN_BOOK && book.getItem() != Items.WRITABLE_BOOK) {
            return BookCheckResult.safe();
        }

        NbtCompound nbt = book.getNbt();
        if (nbt == null) return BookCheckResult.safe();

        // Получаем страницы
        NbtList pages = nbt.getList("pages", 8); // 8 = NbtString
        if (pages == null) return BookCheckResult.safe();

        // Проверка количества страниц
        if (filterTooManyPages.get() && pages.size() > maxPages.get()) {
            return BookCheckResult.dangerous("Слишком много страниц: " + pages.size());
        }

        // Проверка каждой страницы
        int pageIndex = 0;
        for (Object pageObj : pages) {
            String pageText = pageObj.toString();
            pageIndex++;

            // Проверка длины
            if (filterTooManyPages.get() && pageText.length() > maxPageLength.get()) {
                return BookCheckResult.dangerous("Страница " + pageIndex + " слишком длинная: " + pageText.length() + " символов");
            }

            // Проверка цветных кодов
            if (filterColorCodes.get() && pageText.contains("§")) {
                return BookCheckResult.dangerous("Страница " + pageIndex + " содержит цветные коды (§)");
            }

            // Проверка запрещённых символов
            if (filterIllegalCharacters.get()) {
                for (char c : pageText.toCharArray()) {
                    if (c < 32 || c > 126) {
                        return BookCheckResult.dangerous("Страница " + pageIndex + " содержит недопустимый символ: " + c);
                    }
                }
            }

            // Проверка JSON
            if (filterJson.get() && pageText.trim().startsWith("{") && pageText.trim().endsWith("}")) {
                return BookCheckResult.dangerous("Страница " + pageIndex + " содержит JSON");
            }

            // Проверка запрещённых слов
            for (String word : blacklistedWords.get()) {
                if (pageText.toLowerCase().contains(word.toLowerCase())) {
                    return BookCheckResult.dangerous("Страница " + pageIndex + " содержит запрещённое слово: " + word);
                }
            }
        }

        return BookCheckResult.safe();
    }

    // --- УДАЛЕНИЕ КНИГИ ИЗ ИНВЕНТАРЯ ---
    private void removeBook(ItemStack book) {
        if (mc.player == null) return;

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack == book) {
                mc.player.getInventory().setStack(i, ItemStack.EMPTY);
                break;
            }
        }
    }

    // --- СКАНИРОВАНИЕ ИНВЕНТАРЯ (фоновая проверка) ---
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (!scanInventory.get()) return;

        scanCounter++;
        if (scanCounter < scanDelay.get()) return;
        scanCounter = 0;

        // Сканируем весь инвентарь
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() == Items.WRITTEN_BOOK || stack.getItem() == Items.WRITABLE_BOOK) {
                BookCheckResult result = checkBook(stack);
                if (result.isDangerous()) {
                    if (mode.get() == Mode.Remove || mode.get() == Mode.Both) {
                        mc.player.getInventory().setStack(i, ItemStack.EMPTY);
                        if (notify.get()) {
                            ChatUtils.warn("§cУдалена опасная книга из инвентаря! Причина: " + result.getReason());
                        }
                    }
                }
            }
        }
    }

    // --- ВСПОМОГАТЕЛЬНЫЙ КЛАСС ДЛЯ РЕЗУЛЬТАТА ---
    private static class BookCheckResult {
        private final boolean dangerous;
        private final String reason;

        private BookCheckResult(boolean dangerous, String reason) {
            this.dangerous = dangerous;
            this.reason = reason;
        }

        public static BookCheckResult safe() {
            return new BookCheckResult(false, null);
        }

        public static BookCheckResult dangerous(String reason) {
            return new BookCheckResult(true, reason);
        }

        public boolean isDangerous() {
            return dangerous;
        }

        public String getReason() {
            return reason != null ? reason : "Unknown";
        }
    }

    @Override
    public String getInfoString() {
        return mode.get().toString();
    }
}
