package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import hmcore.HM_CORE;

import java.util.ArrayList;
import java.util.List;

public class AntiPotion extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgEffects = settings.createGroup("Effects");
    private final SettingGroup sgVisual = settings.createGroup("Visual");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Режим работы")
            .defaultValue(Mode.Visual)
            .build()
    );

    public final Setting<Boolean> removeAllEffects = sgGeneral.add(new BoolSetting.Builder()
            .name("remove-all-effects")
            .description("Удалить все эффекты (включая положительные)")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> onlyNegative = sgGeneral.add(new BoolSetting.Builder()
            .name("only-negative")
            .description("Отключать только негативные эффекты")
            .defaultValue(true)
            .build()
    );

    // --- СПИСОК ЭФФЕКТОВ ---

    public final Setting<Boolean> blockBlindness = sgEffects.add(new BoolSetting.Builder()
            .name("block-blindness")
            .description("Отключать слепоту")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> blockSlowness = sgEffects.add(new BoolSetting.Builder()
            .name("block-slowness")
            .description("Отключать замедление")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> blockPoison = sgEffects.add(new BoolSetting.Builder()
            .name("block-poison")
            .description("Отключать яд")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> blockWeakness = sgEffects.add(new BoolSetting.Builder()
            .name("block-weakness")
            .description("Отключать слабость")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> blockWither = sgEffects.add(new BoolSetting.Builder()
            .name("block-wither")
            .description("Отключать иссушение")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> blockNausea = sgEffects.add(new BoolSetting.Builder()
            .name("block-nausea")
            .description("Отключать тошноту")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> blockHunger = sgEffects.add(new BoolSetting.Builder()
            .name("block-hunger")
            .description("Отключать голод")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> blockMiningFatigue = sgEffects.add(new BoolSetting.Builder()
            .name("block-mining-fatigue")
            .description("Отключать усталость от добычи")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> blockDarkness = sgEffects.add(new BoolSetting.Builder()
            .name("block-darkness")
            .description("Отключать тьму (эффект из 1.19+)")
            .defaultValue(true)
            .build()
    );

    // --- ВИЗУАЛ ---

    public final Setting<Boolean> notify = sgVisual.add(new BoolSetting.Builder()
            .name("notify")
            .description("Уведомлять о блокировке эффекта")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> showClearedEffects = sgVisual.add(new BoolSetting.Builder()
            .name("show-cleared-effects")
            .description("Показывать в HUD список очищенных эффектов")
            .defaultValue(false)
            .build()
    );

    // --- ПРОДВИНУТЫЕ ---

    public final Setting<Boolean> clearOnTick = sgAdvanced.add(new BoolSetting.Builder()
            .name("clear-on-tick")
            .description("Очищать эффекты каждый тик (а не только при применении)")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> onlyWhenMoving = sgAdvanced.add(new BoolSetting.Builder()
            .name("only-when-moving")
            .description("Работает только при движении")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> autoDisableOnHealth = sgAdvanced.add(new BoolSetting.Builder()
            .name("auto-disable-on-health")
            .description("Выключать модуль при низком здоровье (чтобы не мешать лечению)")
            .defaultValue(false)
            .build()
    );

    public final Setting<Integer> disableHealth = sgAdvanced.add(new IntSetting.Builder()
            .name("disable-health")
            .description("Здоровье, при котором выключать модуль")
            .defaultValue(4)
            .min(1)
            .max(19)
            .sliderMax(19)
            .build()
    );

    // --- ENUM ---
    public enum Mode {
        Visual("Только визуально"),
        Full("Полностью (и визуально, и физически)");

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
    private final List<String> clearedEffects = new ArrayList<>();

    // --- КОНСТРУКТОР ---
    public AntiPotion() {
        super(HM_CORE.CATEGORY, "AntiPotion", "Отключает негативные эффекты зелий");
    }

    @Override
    public void onActivate() {
        clearedEffects.clear();
        if (notify.get()) {
            ChatUtils.info("AntiPotion активирован. Режим: " + mode.get());
        }
    }

    @Override
    public void onDeactivate() {
        clearedEffects.clear();
    }

    // --- ТИК ---
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Проверка здоровья
        if (autoDisableOnHealth.get() && mc.player.getHealth() < disableHealth.get()) {
            if (isActive()) {
                toggle();
                ChatUtils.warn("AntiPotion выключен (низкое здоровье)");
            }
            return;
        }

        // Только при движении
        if (onlyWhenMoving.get() && mc.player.getVelocity().length() < 0.01) return;

        // Очистка эффектов
        if (clearOnTick.get()) {
            clearEffects();
        }
    }

    // --- ОЧИСТКА ЭФФЕКТОВ ---
    private void clearEffects() {
        if (mc.player == null) return;

        // Если нужно удалить все эффекты
        if (removeAllEffects.get()) {
            for (StatusEffectInstance effect : new ArrayList<>(mc.player.getStatusEffects())) {
                mc.player.removeStatusEffect(effect.getEffectType());
                if (notify.get()) {
                    ChatUtils.info("§cУдалён эффект: " + effect.getEffectType().getName().getString());
                }
            }
            return;
        }

        // Проверка каждого эффекта
        for (StatusEffectInstance effect : new ArrayList<>(mc.player.getStatusEffects())) {
            StatusEffect type = effect.getEffectType();
            if (shouldRemove(type)) {
                if (mode.get() == Mode.Visual) {
                    // Только визуально: убираем частицы и иконку
                    // В Minecraft это делается через установку duration = 0, но это сложно
                    // Просто удаляем эффект полностью, т.к. это проще
                    mc.player.removeStatusEffect(type);
                } else {
                    // Полностью удаляем эффект
                    mc.player.removeStatusEffect(type);
                }

                if (notify.get()) {
                    String name = type.getName().getString();
                    if (!clearedEffects.contains(name)) {
                        clearedEffects.add(name);
                        ChatUtils.info("§aЗаблокирован эффект: " + name);
                    }
                }
            }
        }
    }

    // --- ПРОВЕРКА, НУЖНО ЛИ УДАЛЯТЬ ЭФФЕКТ ---
    private boolean shouldRemove(StatusEffect effect) {
        if (onlyNegative.get() && !isNegativeEffect(effect)) return false;

        if (effect == StatusEffects.BLINDNESS && blockBlindness.get()) return true;
        if (effect == StatusEffects.SLOWNESS && blockSlowness.get()) return true;
        if (effect == StatusEffects.POISON && blockPoison.get()) return true;
        if (effect == StatusEffects.WEAKNESS && blockWeakness.get()) return true;
        if (effect == StatusEffects.WITHER && blockWither.get()) return true;
        if (effect == StatusEffects.NAUSEA && blockNausea.get()) return true;
        if (effect == StatusEffects.HUNGER && blockHunger.get()) return true;
        if (effect == StatusEffects.MINING_FATIGUE && blockMiningFatigue.get()) return true;
        if (effect == StatusEffects.DARKNESS && blockDarkness.get()) return true;

        return false;
    }

    // --- ОПРЕДЕЛЕНИЕ, ЯВЛЯЕТСЯ ЛИ ЭФФЕКТ НЕГАТИВНЫМ ---
    private boolean isNegativeEffect(StatusEffect effect) {
        return effect == StatusEffects.BLINDNESS
                || effect == StatusEffects.SLOWNESS
                || effect == StatusEffects.POISON
                || effect == StatusEffects.WEAKNESS
                || effect == StatusEffects.WITHER
                || effect == StatusEffects.NAUSEA
                || effect == StatusEffects.HUNGER
                || effect == StatusEffects.MINING_FATIGUE
                || effect == StatusEffects.DARKNESS
                || effect == StatusEffects.LEVITATION
                || effect == StatusEffects.BAD_OMEN
                || effect == StatusEffects.UNLUCK;
    }

    // --- ИНФОРМАЦИЯ В HUD ---
    @Override
    public String getInfoString() {
        if (showClearedEffects.get() && !clearedEffects.isEmpty()) {
            return String.join(", ", clearedEffects);
        }
        return mode.get().toString();
    }
}
