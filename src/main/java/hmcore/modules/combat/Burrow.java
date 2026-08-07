package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import hmcore.HM_CORE;

public class Burrow extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBlocks = settings.createGroup("Blocks");
    private final SettingGroup sgSafety = settings.createGroup("Safety");
    private final SettingGroup sgVisual = settings.createGroup("Visual");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
            .name("delay")
            .description("Задержка перед закапыванием (мс)")
            .defaultValue(100)
            .min(0)
            .max(500)
            .sliderMax(500)
            .build()
    );

    public final Setting<Boolean> center = sgGeneral.add(new BoolSetting.Builder()
            .name("center")
            .description("Центрировать игрока перед закапыванием")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> onlyWhenEmpty = sgGeneral.add(new BoolSetting.Builder()
            .name("only-when-empty")
            .description("Закапываться только если в блоке пусто")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> autoDisableAfter = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-disable-after")
            .description("Автоматически выключить модуль после закапывания")
            .defaultValue(true)
            .build()
    );

    // --- БЛОКИ ---

    public final Setting<BlockType> blockType = sgBlocks.add(new EnumSetting.Builder<BlockType>()
            .name("block-type")
            .description("Тип блока для закапывания")
            .defaultValue(BlockType.Obsidian)
            .build()
    );

    public final Setting<Boolean> useOffhand = sgBlocks.add(new BoolSetting.Builder()
            .name("use-offhand")
            .description("Использовать оффхэнд для блоков")
            .defaultValue(false)
            .build()
    );

    // --- БЕЗОПАСНОСТЬ ---

    public final Setting<Boolean> antiStuck = sgSafety.add(new BoolSetting.Builder()
            .name("anti-stuck")
            .description("Выталкивать игрока, если он застрял")
            .defaultValue(true)
            .build()
    );

    public final Setting<Integer> stuckCheckDelay = sgSafety.add(new IntSetting.Builder()
            .name("stuck-check-delay")
            .description("Задержка перед проверкой на застревание (тики)")
            .defaultValue(10)
            .min(1)
            .max(50)
            .sliderMax(50)
            .build()
    );

    public final Setting<Boolean> pauseOnDamage = sgSafety.add(new BoolSetting.Builder()
            .name("pause-on-damage")
            .description("Останавливать закапывание при получении урона")
            .defaultValue(false)
            .build()
    );

    // --- ВИЗУАЛ ---

    public final Setting<Boolean> render = sgVisual.add(new BoolSetting.Builder()
            .name("render")
            .description("Показывать место закапывания")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> notify = sgVisual.add(new BoolSetting.Builder()
            .name("notify")
            .description("Уведомлять о закапывании")
            .defaultValue(true)
            .build()
    );

    // --- ENUM ---
    public enum BlockType {
        Obsidian("Обсидиан"),
        Bedrock("Бедрок"),
        CryingObsidian("Плачущий обсидиан"),
        Any("Любой");

        private final String name;

        BlockType(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // --- ПЕРЕМЕННЫЕ ---
    private long lastBurrowTime = 0;
    private boolean hasBurrowed = false;
    private int stuckCheckCounter = 0;

    // --- КОНСТРУКТОР ---
    public Burrow() {
        super(HM_CORE.CATEGORY, "Burrow", "Закапывает игрока в блок (мгновенная защита)");
    }

    @Override
    public void onActivate() {
        lastBurrowTime = 0;
        hasBurrowed = false;
        stuckCheckCounter = 0;

        if (center.get() && mc.player != null) {
            BlockPos pos = mc.player.getBlockPos();
            mc.player.setPosition(pos.getX() + 0.5, mc.player.getY(), pos.getZ() + 0.5);
        }
    }

    @Override
    public void onDeactivate() {
        // Если застрял — вытаскиваем
        if (antiStuck.get() && mc.player != null && mc.player.horizontalCollision) {
            mc.player.setPosition(mc.player.getX(), mc.player.getY() + 0.5, mc.player.getZ());
        }
        hasBurrowed = false;
    }

    // --- ТИК ---
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Проверка урона
        if (pauseOnDamage.get() && mc.player.hurtTime > 0) return;

        // Если уже закопались — дальше не идём
        if (hasBurrowed) {
            // Проверка на застревание
            stuckCheckCounter++;
            if (antiStuck.get() && stuckCheckCounter >= stuckCheckDelay.get()) {
                if (mc.player.horizontalCollision) {
                    mc.player.setPosition(mc.player.getX(), mc.player.getY() + 0.5, mc.player.getZ());
                    if (notify.get()) {
                        info("Вытаскиваем из блока (застревание)");
                    }
                }
                stuckCheckCounter = 0;
            }
            return;
        }

        if (System.currentTimeMillis() - lastBurrowTime < delay.get()) return;

        BlockPos pos = mc.player.getBlockPos();

        // Проверка на пустоту
        if (onlyWhenEmpty.get() && !mc.world.getBlockState(pos).isAir()) return;

        // Проверка, есть ли блок в инвентаре
        if (!hasBlock()) return;

        // Ставим блок
        if (placeBlock(pos)) {
            hasBurrowed = true;
            lastBurrowTime = System.currentTimeMillis();

            if (notify.get()) {
                info("§aЗакопались! §7Блок: " + blockType.get().toString());
            }

            // Автовыключение
            if (autoDisableAfter.get()) {
                toggle();
                info("§7Burrow автоматически выключен");
            }
        }
    }

    // --- ПРОВЕРКА НАЛИЧИЯ БЛОКА ---
    private boolean hasBlock() {
        switch (blockType.get()) {
            case Obsidian -> {
                return InvUtils.findInHotbar(Items.OBSIDIAN).found();
            }
            case Bedrock -> {
                return InvUtils.findInHotbar(Items.BEDROCK).found();
            }
            case CryingObsidian -> {
                return InvUtils.findInHotbar(Items.CRYING_OBSIDIAN).found();
            }
            case Any -> {
                return InvUtils.findInHotbar(Items.OBSIDIAN).found()
                        || InvUtils.findInHotbar(Items.BEDROCK).found()
                        || InvUtils.findInHotbar(Items.CRYING_OBSIDIAN).found();
            }
            default -> {
                return false;
            }
        }
    }

    // --- УСТАНОВКА БЛОКА ---
    private boolean placeBlock(BlockPos pos) {
        if (blockType.get() == BlockType.Obsidian && InvUtils.findInHotbar(Items.OBSIDIAN).found()) {
            BlockUtils.place(pos, InvUtils.findInHotbar(Items.OBSIDIAN), true, 0);
            return true;
        }
        if (blockType.get() == BlockType.Bedrock && InvUtils.findInHotbar(Items.BEDROCK).found()) {
            BlockUtils.place(pos, InvUtils.findInHotbar(Items.BEDROCK), true, 0);
            return true;
        }
        if (blockType.get() == BlockType.CryingObsidian && InvUtils.findInHotbar(Items.CRYING_OBSIDIAN).found()) {
            BlockUtils.place(pos, InvUtils.findInHotbar(Items.CRYING_OBSIDIAN), true, 0);
            return true;
        }
        if (blockType.get() == BlockType.Any) {
            if (InvUtils.findInHotbar(Items.OBSIDIAN).found()) {
                BlockUtils.place(pos, InvUtils.findInHotbar(Items.OBSIDIAN), true, 0);
                return true;
            }
            if (InvUtils.findInHotbar(Items.BEDROCK).found()) {
                BlockUtils.place(pos, InvUtils.findInHotbar(Items.BEDROCK), true, 0);
                return true;
            }
            if (InvUtils.findInHotbar(Items.CRYING_OBSIDIAN).found()) {
                BlockUtils.place(pos, InvUtils.findInHotbar(Items.CRYING_OBSIDIAN), true, 0);
                return true;
            }
        }
        return false;
    }

    @Override
    public String getInfoString() {
        return hasBurrowed ? "§aЗакопался" : "§7Ожидание...";
    }
}
