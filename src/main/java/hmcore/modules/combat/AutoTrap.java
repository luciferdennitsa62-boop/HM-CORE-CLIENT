package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import hmcore.HM_CORE;

import java.util.ArrayList;
import java.util.List;

public class AutoTrap extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBlocks = settings.createGroup("Blocks");
    private final SettingGroup sgTarget = settings.createGroup("Target");
    private final SettingGroup sgSafety = settings.createGroup("Safety");
    private final SettingGroup sgVisual = settings.createGroup("Visual");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
            .name("range")
            .description("Дальность до цели")
            .defaultValue(4.0)
            .min(1.0)
            .max(8.0)
            .sliderMax(8.0)
            .build()
    );

    public final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
            .name("delay")
            .description("Задержка между установкой блоков (мс)")
            .defaultValue(50)
            .min(0)
            .max(500)
            .sliderMax(500)
            .build()
    );

    public final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
            .name("blocks-per-tick")
            .description("Сколько блоков ставить за тик")
            .defaultValue(2)
            .min(1)
            .max(8)
            .sliderMax(8)
            .build()
    );

    public final Setting<Boolean> centerTrap = sgGeneral.add(new BoolSetting.Builder()
            .name("center-trap")
            .description("Центрировать клетку вокруг цели")
            .defaultValue(true)
            .build()
    );

    // --- БЛОКИ ---

    public final Setting<BlockType> blockType = sgBlocks.add(new EnumSetting.Builder<BlockType>()
            .name("block-type")
            .description("Тип блока для постройки")
            .defaultValue(BlockType.Obsidian)
            .build()
    );

    public final Setting<Boolean> includeRoof = sgBlocks.add(new BoolSetting.Builder()
            .name("include-roof")
            .description("Ставить потолок")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> includeFloor = sgBlocks.add(new BoolSetting.Builder()
            .name("include-floor")
            .description("Ставить пол")
            .defaultValue(false)
            .build()
    );

    // --- НАСТРОЙКИ ЦЕЛИ ---

    public final Setting<SortPriority> targetPriority = sgTarget.add(new EnumSetting.Builder<SortPriority>()
            .name("target-priority")
            .description("Приоритет цели")
            .defaultValue(SortPriority.LowestHealth)
            .build()
    );

    public final Setting<Boolean> playersOnly = sgTarget.add(new BoolSetting.Builder()
            .name("players-only")
            .description("Только игроки")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> antiFriend = sgTarget.add(new BoolSetting.Builder()
            .name("anti-friend")
            .description("Игнорировать друзей")
            .defaultValue(false)
            .build()
    );

    public final Setting<List<String>> friendsList = sgTarget.add(new StringListSetting.Builder()
            .name("friends-list")
            .description("Список друзей (через запятую)")
            .defaultValue()
            .build()
    );

    // --- БЕЗОПАСНОСТЬ ---

    public final Setting<Boolean> onlyIfVisible = sgSafety.add(new BoolSetting.Builder()
            .name("only-if-visible")
            .description("Строить только если цель видна")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> pauseOnDamage = sgSafety.add(new BoolSetting.Builder()
            .name("pause-on-damage")
            .description("Останавливать постройку при получении урона")
            .defaultValue(false)
            .build()
    );

    // --- ВИЗУАЛ ---

    public final Setting<Boolean> render = sgVisual.add(new BoolSetting.Builder()
            .name("render")
            .description("Показывать места установки")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> notify = sgVisual.add(new BoolSetting.Builder()
            .name("notify")
            .description("Уведомлять о постройке ловушки")
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
    private long lastPlaceTime = 0;
    private PlayerEntity target = null;
    private BlockPos[] trapPositions = new BlockPos[0];

    // --- КОНСТРУКТОР ---
    public AutoTrap() {
        super(HM_CORE.CATEGORY, "AutoTrap", "Строит клетку из обсидиана вокруг врага (ловушка)");
    }

    @Override
    public void onActivate() {
        lastPlaceTime = 0;
        target = null;
        trapPositions = new BlockPos[0];
    }

    @Override
    public void onDeactivate() {
        target = null;
        trapPositions = new BlockPos[0];
    }

    // --- ТИК ---
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Проверка урона
        if (pauseOnDamage.get() && mc.player.hurtTime > 0) return;

        // Выбор цели
        target = (PlayerEntity) TargetUtils.getPlayerTarget(range.get(), targetPriority.get());
        if (target == null) {
            trapPositions = new BlockPos[0];
            return;
        }

        // Проверка видимости
        if (onlyIfVisible.get() && !mc.player.canSee(target)) return;

        // Проверка друзей
        if (antiFriend.get()) {
            String name = target.getName().getString();
            for (String friend : friendsList.get()) {
                if (name.equalsIgnoreCase(friend.trim())) {
                    return;
                }
            }
        }

        // Обновляем позиции для ловушки
        trapPositions = getTrapPositions();

        // Проверка задержки
        if (System.currentTimeMillis() - lastPlaceTime < delay.get()) return;

        // Строим
        int placed = 0;
        for (BlockPos pos : trapPositions) {
            if (shouldPlace(pos)) {
                if (placeBlock(pos)) {
                    placed++;
                }
            }
            if (placed >= blocksPerTick.get()) break;
        }

        if (placed > 0) {
            lastPlaceTime = System.currentTimeMillis();
            if (notify.get() && placed > 0) {
                info("§eСтроим ловушку для: " + target.getName().getString());
            }
        }
    }

    // --- ПОЛУЧЕНИЕ ПОЗИЦИЙ ДЛЯ ЛОВУШКИ ---
    private BlockPos[] getTrapPositions() {
        if (target == null) return new BlockPos[0];

        BlockPos targetPos = target.getBlockPos();
        if (centerTrap.get()) {
            targetPos = new BlockPos(
                    targetPos.getX() - (targetPos.getX() % 1),
                    targetPos.getY(),
                    targetPos.getZ() - (targetPos.getZ() % 1)
            );
        }

        List<BlockPos> positions = new ArrayList<>();

        // 4 стены вокруг цели
        BlockPos[] walls = {
                targetPos.add(1, 0, 0),  // восток
                targetPos.add(-1, 0, 0), // запад
                targetPos.add(0, 0, 1),  // юг
                targetPos.add(0, 0, -1)  // север
        };

        for (BlockPos wall : walls) {
            positions.add(wall);
            // Стена на уровне головы
            positions.add(wall.up());
        }

        // Потолок
        if (includeRoof.get()) {
            positions.add(targetPos.add(0, 2, 0));
            positions.add(targetPos.add(1, 2, 0));
            positions.add(targetPos.add(-1, 2, 0));
            positions.add(targetPos.add(0, 2, 1));
            positions.add(targetPos.add(0, 2, -1));
        }

        // Пол
        if (includeFloor.get()) {
            positions.add(targetPos.add(0, -1, 0));
            positions.add(targetPos.add(1, -1, 0));
            positions.add(targetPos.add(-1, -1, 0));
            positions.add(targetPos.add(0, -1, 1));
            positions.add(targetPos.add(0, -1, -1));
        }

        return positions.toArray(new BlockPos[0]);
    }

    // --- ПРОВЕРКА НУЖНО ЛИ СТАВИТЬ ---
    private boolean shouldPlace(BlockPos pos) {
        if (mc.world == null) return false;
        return mc.world.getBlockState(pos).isAir() || mc.world.getBlockState(pos).getBlock() == Blocks.WATER;
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
        if (target != null) {
            return "§c" + target.getName().getString();
        }
        return "§7Ожидание...";
    }
}
