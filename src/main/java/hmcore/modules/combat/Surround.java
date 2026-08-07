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
import net.minecraft.util.math.Direction;
import hmcore.HM_CORE;

import java.util.ArrayList;
import java.util.List;

public class Surround extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBlocks = settings.createGroup("Blocks");
    private final SettingGroup sgVisual = settings.createGroup("Visual");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Режим обстройки")
            .defaultValue(Mode.Full)
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
            .description("Сколько блоков ставить за один тик")
            .defaultValue(2)
            .min(1)
            .max(8)
            .sliderMax(8)
            .build()
    );

    public final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
            .name("rotate")
            .description("Поворачивать голову к месту установки (для обхода античитов)")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> center = sgGeneral.add(new BoolSetting.Builder()
            .name("center")
            .description("Автоматически центрировать игрока перед установкой")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> replace = sgGeneral.add(new BoolSetting.Builder()
            .name("replace")
            .description("Заменять существующие блоки (например, если уже есть обсидиан, не ставить поверх)")
            .defaultValue(true)
            .build()
    );

    // --- НАСТРОЙКИ БЛОКОВ ---

    public final Setting<Integer> radius = sgBlocks.add(new IntSetting.Builder()
            .name("radius")
            .description("Радиус обстройки (1 – только 4 блока вокруг, 2 – 12 блоков, 3 – 24 блока)")
            .defaultValue(1)
            .min(1)
            .max(3)
            .sliderMax(3)
            .build()
    );

    public final Setting<Boolean> useObsidian = sgBlocks.add(new BoolSetting.Builder()
            .name("use-obsidian")
            .description("Использовать обсидиан")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> useBedrock = sgBlocks.add(new BoolSetting.Builder()
            .name("use-bedrock")
            .description("Использовать бедрок (только если есть в инвентаре)")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> useCryingObsidian = sgBlocks.add(new BoolSetting.Builder()
            .name("use-crying-obsidian")
            .description("Использовать плачущий обсидиан")
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

    public final Setting<Boolean> renderFilled = sgVisual.add(new BoolSetting.Builder()
            .name("render-filled")
            .description("Заливать блоки цветом")
            .defaultValue(false)
            .build()
    );

    // --- ПЕРЕМЕННЫЕ ---

    private long lastPlaceTime = 0;
    private final List<BlockPos> positions = new ArrayList<>();

    // --- ENUM ---

    public enum Mode {
        Full("Все 4 стороны"),
        Floor("Только пол"),
        Walls("Только стены"),
        Roof("Только потолок");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // --- КОНСТРУКТОР ---

    public Surround() {
        super(HM_CORE.CATEGORY, "Surround", "Автоматическая обстройка себя обсидианом");
    }

    @Override
    public void onActivate() {
        positions.clear();
        lastPlaceTime = 0;
        if (center.get() && mc.player != null) {
            // Центрирование игрока
            BlockPos pos = mc.player.getBlockPos();
            double centerX = pos.getX() + 0.5;
            double centerZ = pos.getZ() + 0.5;
            mc.player.setPosition(centerX, mc.player.getY(), centerZ);
        }
    }

    @Override
    public void onDeactivate() {
        positions.clear();
    }

    // --- ОСНОВНОЙ ТИК ---

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Обновляем список позиций для обстройки
        updatePositions();

        // Проверяем задержку
        if (System.currentTimeMillis() - lastPlaceTime < delay.get()) return;

        // Берём блоки, которые нужно поставить (с учетом replace)
        List<BlockPos> toPlace = new ArrayList<>();
        for (BlockPos pos : positions) {
            // Проверяем, можно ли ставить блок (воздух или заменяемый блок)
            if (replace.get()) {
                // Если блок уже нужный (обсидиан, бедрок) – пропускаем
                if (isBlockValid(pos)) continue;
            } else {
                // Если replace выключен – ставим только на воздух
                if (!mc.world.getBlockState(pos).isAir()) continue;
            }
            toPlace.add(pos);
        }

        // Ограничиваем количество блоков за тик
        int count = Math.min(toPlace.size(), blocksPerTick.get());
        for (int i = 0; i < count; i++) {
            BlockPos pos = toPlace.get(i);
            placeBlock(pos);
        }

        if (!toPlace.isEmpty()) {
            lastPlaceTime = System.currentTimeMillis();
        }
    }

    // --- ОБНОВЛЕНИЕ СПИСКА ПОЗИЦИЙ ---

    private void updatePositions() {
        positions.clear();
        BlockPos playerPos = mc.player.getBlockPos();

        // В зависимости от режима и радиуса добавляем позиции
        int r = radius.get();

        // Все возможные позиции вокруг игрока
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                // Пропускаем центр (где стоит игрок)
                if (x == 0 && z == 0) continue;
                // Проверяем, что это блок на уровне ног игрока (y = 0)
                BlockPos pos = playerPos.add(x, 0, z);
                // Проверяем расстояние по кругу (не квадрат)
                if (Math.sqrt(x*x + z*z) > r + 0.1) continue;

                // Добавляем в зависимости от режима
                switch (mode.get()) {
                    case Full:
                        addPositionsForFull(pos);
                        break;
                    case Floor:
                        addPositionsForFloor(pos);
                        break;
                    case Walls:
                        addPositionsForWalls(pos);
                        break;
                    case Roof:
                        addPositionsForRoof(pos);
                        break;
                }
            }
        }
    }

    // --- ДОБАВЛЕНИЕ ПОЗИЦИЙ ДЛЯ РАЗНЫХ РЕЖИМОВ ---

    private void addPositionsForFull(BlockPos pos) {
        // Ставим блок на уровне ног
        positions.add(pos);
        // Ставим блок над головой (y+1) и под ногами (y-1) – опционально
        // Чтобы не перегружать, ограничимся уровнем ног для Full
    }

    private void addPositionsForFloor(BlockPos pos) {
        // Только пол (y = 0)
        positions.add(pos.down());
    }

    private void addPositionsForWalls(BlockPos pos) {
        // Только стены (уровень ног и над головой)
        positions.add(pos);
        positions.add(pos.up());
    }

    private void addPositionsForRoof(BlockPos pos) {
        // Только потолок (y+1)
        positions.add(pos.up());
    }

    // --- УСТАНОВКА БЛОКА ---

    private void placeBlock(BlockPos pos) {
        // Выбираем тип блока
        boolean placed = false;
        if (useBedrock.get() && InvUtils.findInHotbar(Items.BEDROCK).found()) {
            BlockUtils.place(pos, InvUtils.findInHotbar(Items.BEDROCK), rotate.get(), 0);
            placed = true;
        } else if (useCryingObsidian.get() && InvUtils.findInHotbar(Items.CRYING_OBSIDIAN).found()) {
            BlockUtils.place(pos, InvUtils.findInHotbar(Items.CRYING_OBSIDIAN), rotate.get(), 0);
            placed = true;
        } else if (useObsidian.get() && InvUtils.findInHotbar(Items.OBSIDIAN).found()) {
            BlockUtils.place(pos, InvUtils.findInHotbar(Items.OBSIDIAN), rotate.get(), 0);
            placed = true;
        }

        if (!placed) {
            // Если ничего не нашли – пробуем обсидиан (если есть)
            if (InvUtils.findInHotbar(Items.OBSIDIAN).found()) {
                BlockUtils.place(pos, InvUtils.findInHotbar(Items.OBSIDIAN), rotate.get(), 0);
            }
        }
    }

    // --- ПРОВЕРКА БЛОКА НА ВАЛИДНОСТЬ ---

    private boolean isBlockValid(BlockPos pos) {
        // Проверяем, является ли блок обсидианом, бедроком или плачущим обсидианом
        return mc.world.getBlockState(pos).isOf(Blocks.OBSIDIAN)
                || mc.world.getBlockState(pos).isOf(Blocks.BEDROCK)
                || mc.world.getBlockState(pos).isOf(Blocks.CRYING_OBSIDIAN);
    }

    // --- РЕНДЕРИНГ (заглушка) ---

    @EventHandler
    private void onTickRender(TickEvent.Post event) {
        if (render.get() && mc.world != null) {
            // Здесь можно рисовать кубы на местах установки
            // Для простоты оставим пустым
        }
    }
}
