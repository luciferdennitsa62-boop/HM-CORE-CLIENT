package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import hmcore.HM_CORE;

import java.util.ArrayList;
import java.util.List;

public class SelfTrap extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBlocks = settings.createGroup("Blocks");
    private final SettingGroup sgSafety = settings.createGroup("Safety");
    private final SettingGroup sgVisual = settings.createGroup("Visual");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

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
            .description("Сколько блоков ставить за один тик")
            .defaultValue(2)
            .min(1)
            .max(8)
            .sliderMax(8)
            .build()
    );

    public final Setting<Integer> towerHeight = sgGeneral.add(new IntSetting.Builder()
            .name("tower-height")
            .description("Сколько блоков ставить над головой (1-3)")
            .defaultValue(2)
            .min(1)
            .max(3)
            .sliderMax(3)
            .build()
    );

    public final Setting<Boolean> center = sgGeneral.add(new BoolSetting.Builder()
            .name("center")
            .description("Центрировать игрока перед установкой")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
            .name("rotate")
            .description("Поворачивать голову к месту установки")
            .defaultValue(false)
            .build()
    );

    // --- БЛОКИ ---

    public final Setting<BlockType> blockType = sgBlocks.add(new EnumSetting.Builder<BlockType>()
            .name("block-type")
            .description("Тип блока для постройки")
            .defaultValue(BlockType.Obsidian)
            .build()
    );

    public final Setting<Boolean> replaceBlocks = sgBlocks.add(new BoolSetting.Builder()
            .name("replace-blocks")
            .description("Заменять существующие блоки (если они не обсидиан)")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> useOffhand = sgBlocks.add(new BoolSetting.Builder()
            .name("use-offhand")
            .description("Использовать оффхэнд для блоков")
            .defaultValue(false)
            .build()
    );

    // --- БЕЗОПАСНОСТЬ ---

    public final Setting<Boolean> onlyWhenCrystalNearby = sgSafety.add(new BoolSetting.Builder()
            .name("only-when-crystal-nearby")
            .description("Ставить блоки только если рядом кристалл")
            .defaultValue(false)
            .build()
    );

    public final Setting<Double> crystalRange = sgSafety.add(new DoubleSetting.Builder()
            .name("crystal-range")
            .description("Дистанция до кристалла для активации")
            .defaultValue(4.0)
            .min(1.0)
            .max(8.0)
            .sliderMax(8.0)
            .build()
    );

    public final Setting<Boolean> antiStuck = sgSafety.add(new BoolSetting.Builder()
            .name("anti-stuck")
            .description("Вытаскивать игрока, если он застрял в блоке")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> pauseOnHit = sgSafety.add(new BoolSetting.Builder()
            .name("pause-on-hit")
            .description("Останавливать установку при получении урона")
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

    public final Setting<Boolean> sound = sgVisual.add(new BoolSetting.Builder()
            .name("sound")
            .description("Звук при установке блока")
            .defaultValue(false)
            .build()
    );

    // --- ПРОДВИНУТЫЕ ---

    public final Setting<Boolean> smartMode = sgAdvanced.add(new BoolSetting.Builder()
            .name("smart-mode")
            .description("Умный режим: ставить только когда нужно")
            .defaultValue(true)
            .build()
    );

    public final Setting<Integer> maxBlocks = sgAdvanced.add(new IntSetting.Builder()
            .name("max-blocks")
            .description("Максимальное количество блоков для установки (0 = безлимит)")
            .defaultValue(0)
            .min(0)
            .max(64)
            .sliderMax(64)
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
    private int totalPlaced = 0;
    private BlockPos[] positions = new BlockPos[0];

    // --- КОНСТРУКТОР ---
    public SelfTrap() {
        super(HM_CORE.CATEGORY, "SelfTrap", "Ставит обсидиан над головой (защита от кристаллов)");
    }

    @Override
    public void onActivate() {
        totalPlaced = 0;
        lastPlaceTime = 0;
        if (center.get() && mc.player != null) {
            BlockPos pos = mc.player.getBlockPos();
            mc.player.setPosition(pos.getX() + 0.5, mc.player.getY(), pos.getZ() + 0.5);
        }
    }

    // --- ТИК ---
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Проверка урона
        if (pauseOnHit.get() && mc.player.hurtTime > 0) return;

        // Проверка кристалла
        if (onlyWhenCrystalNearby.get() && !isCrystalNearby()) return;

        // Лимит блоков
        if (maxBlocks.get() > 0 && totalPlaced >= maxBlocks.get()) return;

        if (System.currentTimeMillis() - lastPlaceTime < delay.get()) return;

        // Обновляем позиции
        updatePositions();

        // Проверка, надо ли ставить
        if (smartMode.get() && isTrapComplete()) return;

        // Установка
        int placed = 0;
        for (BlockPos pos : positions) {
            if (shouldPlace(pos)) {
                if (placeBlock(pos)) {
                    placed++;
                    totalPlaced++;
                }
            }
            if (placed >= blocksPerTick.get()) break;
        }

        if (placed > 0) {
            lastPlaceTime = System.currentTimeMillis();
            if (sound.get() && mc.player != null) {
                // Звук установки
            }
        }

        // Anti-stuck
        if (antiStuck.get() && mc.player.horizontalCollision) {
            mc.player.setVelocity(mc.player.getVelocity().x, 0.1, mc.player.getVelocity().z);
        }
    }

    // --- ОБНОВЛЕНИЕ ПОЗИЦИЙ ---
    private void updatePositions() {
        if (mc.player == null) return;
        BlockPos base = mc.player.getBlockPos();
        List<BlockPos> posList = new ArrayList<>();

        for (int i = 1; i <= towerHeight.get(); i++) {
            posList.add(base.up(i));
        }

        positions = posList.toArray(new BlockPos[0]);
    }

    // --- ПРОВЕРКА НАДО ЛИ СТАВИТЬ ---
    private boolean shouldPlace(BlockPos pos) {
        if (pos == null) return false;
        if (mc.world == null) return false;

        if (replaceBlocks.get()) {
            return !isBlockValid(pos);
        } else {
            return mc.world.getBlockState(pos).isAir() || mc.world.getBlockState(pos).getBlock() == Blocks.WATER;
        }
    }

    // --- ПРОВЕРКА БЛОКА НА ВАЛИДНОСТЬ ---
    private boolean isBlockValid(BlockPos pos) {
        if (mc.world == null) return false;
        return mc.world.getBlockState(pos).isOf(Blocks.OBSIDIAN)
                || mc.world.getBlockState(pos).isOf(Blocks.BEDROCK)
                || mc.world.getBlockState(pos).isOf(Blocks.CRYING_OBSIDIAN);
    }

    // --- ПРОВЕРКА ЗАВЕРШЁННОСТИ КЛЕТКИ ---
    private boolean isTrapComplete() {
        if (mc.world == null) return false;
        for (BlockPos pos : positions) {
            if (!isBlockValid(pos)) return false;
        }
        return true;
    }

    // --- ПРОВЕРКА КРИСТАЛЛА РЯДОМ ---
    private boolean isCrystalNearby() {
        if (mc.world == null) return false;
        Box box = mc.player.getBoundingBox().expand(crystalRange.get());
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof EndCrystalEntity && entity.getBoundingBox().intersects(box)) {
                return true;
            }
        }
        return false;
    }

    // --- УСТАНОВКА БЛОКА ---
    private boolean placeBlock(BlockPos pos) {
        if (blockType.get() == BlockType.Obsidian && InvUtils.findInHotbar(Items.OBSIDIAN).found()) {
            BlockUtils.place(pos, InvUtils.findInHotbar(Items.OBSIDIAN), rotate.get(), 0);
            return true;
        }
        if (blockType.get() == BlockType.Bedrock && InvUtils.findInHotbar(Items.BEDROCK).found()) {
            BlockUtils.place(pos, InvUtils.findInHotbar(Items.BEDROCK), rotate.get(), 0);
            return true;
        }
        if (blockType.get() == BlockType.CryingObsidian && InvUtils.findInHotbar(Items.CRYING_OBSIDIAN).found()) {
            BlockUtils.place(pos, InvUtils.findInHotbar(Items.CRYING_OBSIDIAN), rotate.get(), 0);
            return true;
        }
        if (blockType.get() == BlockType.Any) {
            if (InvUtils.findInHotbar(Items.OBSIDIAN).found()) {
                BlockUtils.place(pos, InvUtils.findInHotbar(Items.OBSIDIAN), rotate.get(), 0);
                return true;
            }
            if (InvUtils.findInHotbar(Items.BEDROCK).found()) {
                BlockUtils.place(pos, InvUtils.findInHotbar(Items.BEDROCK), rotate.get(), 0);
                return true;
            }
            if (InvUtils.findInHotbar(Items.CRYING_OBSIDIAN).found()) {
                BlockUtils.place(pos, InvUtils.findInHotbar(Items.CRYING_OBSIDIAN), rotate.get(), 0);
                return true;
            }
        }
        return false;
    }

    @Override
    public String getInfoString() {
        return blockType.get().toString() + " | " + positions.length + " blocks";
    }
}
