package hmcore.commands;

import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ToggleCommand extends Command {

    public ToggleCommand() {
        super("toggle", "Мощное управление модулями", ".toggle", ".toggle <имя>", ".toggle all <on/off>", ".toggle <категория> <on/off>", ".toggle list", ".toggle search <текст>");
    }

    @Override
    public void run(String[] args) {
        if (args.length == 0) {
            showHelp();
            return;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "help" -> showHelp();
            case "list" -> listModules(args);
            case "search" -> searchModules(args);
            case "all" -> toggleAll(args);
            case "category" -> toggleCategory(args);
            default -> toggleModule(args);
        }
    }

    // --- ПОКАЗАТЬ СПРАВКУ ---

    private void showHelp() {
        info("--- §6ToggleCommand §7— управление модулями ---");
        info("§7.toggle §6<имя> §7— включить/выключить модуль");
        info("§7.toggle §6all on/off §7— все модули");
        info("§7.toggle §6category <имя> on/off §7— категория");
        info("§7.toggle §6list §7— список модулей");
        info("§7.toggle §6search <текст> §7— поиск модулей");
        info("§7.toggle §6help §7— эта справка");
        info("§7Пример: §6.toggle SmartFly");
        info("§7Пример: §6.toggle category Combat on");
    }

    // --- СПИСОК МОДУЛЕЙ ---

    private void listModules(String[] args) {
        List<Module> modules = Modules.get().getAll();
        modules.sort(Comparator.comparing(m -> m.name));

        info("--- §6Все модули (§f" + modules.size() + "§6) ---");

        String currentCategory = "";
        for (Module module : modules) {
            if (!module.category.name.equals(currentCategory)) {
                currentCategory = module.category.name;
                info("§7--- " + currentCategory + " ---");
            }
            String status = module.isActive() ? "§a✔" : "§7✘";
            info("§6" + module.name + " §7" + status);
        }
    }

    // --- ПОИСК МОДУЛЕЙ ---

    private void searchModules(String[] args) {
        if (args.length < 2) {
            error("Укажи текст для поиска! §7.toggle search <текст>");
            return;
        }

        String query = args[1].toLowerCase();
        List<Module> results = Modules.get().getAll().stream()
                .filter(m -> m.name.toLowerCase().contains(query) ||
                        (m.description != null && m.description.toLowerCase().contains(query)) ||
                        m.category.name.toLowerCase().contains(query))
                .collect(Collectors.toList());

        if (results.isEmpty()) {
            error("Ничего не найдено по запросу: §6" + query);
            return;
        }

        info("--- §6Результаты поиска по: §f" + query + " §6(§f" + results.size() + "§6) ---");
        for (Module module : results) {
            String status = module.isActive() ? "§a✔" : "§7✘";
            info("§6" + module.name + " §7[" + module.category.name + "] §7" + status);
        }
    }

    // --- ВКЛЮЧИТЬ/ВЫКЛЮЧИТЬ ВСЕ МОДУЛИ ---

    private void toggleAll(String[] args) {
        if (args.length < 2) {
            error("Укажи on или off! §7.toggle all on/off");
            return;
        }

        boolean enable = args[1].equalsIgnoreCase("on");
        List<Module> modules = Modules.get().getAll();

        for (Module module : modules) {
            if (module.isActive() != enable) {
                module.toggle();
            }
        }

        info("§6Все модули " + (enable ? "§aвключены" : "§cвыключены") + "§7!");
    }

    // --- ВКЛЮЧИТЬ/ВЫКЛЮЧИТЬ КАТЕГОРИЮ ---

    private void toggleCategory(String[] args) {
        if (args.length < 3) {
            error("Укажи категорию и on/off! §7.toggle category <имя> on/off");
            error("§7Доступные категории: §6Combat, Player, Movement, Render, World, Misc, Search, HM-CORE");
            return;
        }

        String categoryName = args[1];
        boolean enable = args[2].equalsIgnoreCase("on");

        // Ищем категорию
        Category targetCategory = null;
        for (Category category : Modules.get().getAllCategories()) {
            if (category.name.equalsIgnoreCase(categoryName)) {
                targetCategory = category;
                break;
            }
        }

        if (targetCategory == null) {
            error("Категория §6" + categoryName + " §7не найдена!");
            return;
        }

        List<Module> modules = Modules.get().getAll().stream()
                .filter(m -> m.category == targetCategory)
                .collect(Collectors.toList());

        for (Module module : modules) {
            if (module.isActive() != enable) {
                module.toggle();
            }
        }

        info("§6Категория " + targetCategory.name + " " + (enable ? "§aвключена" : "§cвыключена") + "§7!");
    }

    // --- ВКЛЮЧИТЬ/ВЫКЛЮЧИТЬ КОНКРЕТНЫЙ МОДУЛЬ ---

    private void toggleModule(String[] args) {
        String name = args[0];

        // Ищем модуль (точное совпадение, потом частичное)
        Module module = Modules.get().get(name);
        if (module == null) {
            // Поиск по части имени
            List<Module> matches = Modules.get().getAll().stream()
                    .filter(m -> m.name.toLowerCase().contains(name.toLowerCase()))
                    .collect(Collectors.toList());

            if (matches.size() == 1) {
                module = matches.get(0);
            } else if (matches.size() > 1) {
                error("Найдено несколько модулей: " + matches.stream().map(m -> m.name).collect(Collectors.joining(", ")));
                return;
            } else {
                error("Модуль §6" + name + " §7не найден!");
                return;
            }
        }

        module.toggle();
        info(module.isActive() ? "§aВключен §7: " + module.name : "§cВыключен §7: " + module.name);

        // Дополнительная информация
        if (module.isActive()) {
            String desc = module.description != null ? module.description : "Без описания";
            info("§7Описание: §f" + desc);
        }
    }

    // --- АВТОДОПОЛНЕНИЕ (для удобства) ---

    @Override
    public void run(String[] args) {
        // Это переопределение для обработки автодополнения
        // В Meteor автодополнение работает автоматически через Completer
    }
}
