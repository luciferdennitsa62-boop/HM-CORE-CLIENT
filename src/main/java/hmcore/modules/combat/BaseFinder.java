package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import hmcore.HM_CORE;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class BaseFinder extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBlocks = settings.createGroup("Blocks");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
            .name("radius")
            .description("Радиус поиска (в блоках)")
            .defaultValue(64)
            .min(10)
            .max(256)
            .sliderMax(256)
            .build()
    );

    public final Setting<Integer> scanDelay = sgGeneral.add(new IntSetting.Builder()
            .name("scan-delay")
            .description("Задержка между сканированиями (тики)")
            .defaultValue(10)
            .min(1)
            .max(100)
            .sliderMax(100)
            .build()
    );

    public final Setting<Boolean> autoScan = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-scan")
            .description("Автоматическое сканирование при движении")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> scanOnActivate = sgGeneral.add(new BoolSetting.Builder()
            .name("scan-on-activate")
            .description("Сканировать при включении модуля")
            .defaultValue(true)
            .build()
    );

    // --- НАСТРОЙКИ БЛОКОВ ---

    public final Setting<Boolean> chests = sgBlocks.add(new BoolSetting.Builder()
            .name("chests")
            .description("Искать сундуки")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> furnaces = sgBlocks.add(new BoolSetting.Builder()
            .name("furnaces")
            .description("Искать печи")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> anvils = sgBlocks.add(new BoolSetting.Builder()
            .name("anvils")
            .description("Искать наковальни")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> beds = sgBlocks.add(new BoolSetting.Builder()
            .name("beds")
            .description("Искать кровати")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> portals = sgBlocks.add(new BoolSetting.Builder()
            .name("portals")
            .description("Искать порталы в Нижний мир")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> craftTables = sgBlocks.add(new BoolSetting.Builder()
            .name("craft-tables")
            .description("Искать верстаки")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> enchantingTables = sgBlocks.add(new BoolSetting.Builder()
            .name("enchanting-tables")
            .description("Искать столы зачарований")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> beacons = sgBlocks.add(new BoolSetting.Builder()
            .name("beacons")
            .description("Искать маяки")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> shulkers = sgBlocks.add(new BoolSetting.Builder()
            .name("shulkers")
            .description("Искать шалкер-ящики")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> barrels = sgBlocks.add(new BoolSetting.Builder()
            .name("barrels")
            .description("Искать бочки")
            .defaultValue(true)
            .build()
    );

    // --- РЕНДЕРИНГ ---

    public final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
            .name("render")
            .description("Показывать найденные блоки")
            .defaultValue(true)
            .build()
    );

    public final Setting<Color> renderColor = sgRender.add(new ColorSetting.Builder()
            .name("render-color")
            .description("Цвет подсветки")
            .defaultValue(new Color(0x00FF00))
            .build()
    );

    public final Setting<Double> renderAlpha = sgRender.add(new DoubleSetting.Builder()
            .name("render-alpha")
            .description("Прозрачность подсветки")
            .defaultValue(0.3)
            .min(0.0)
            .max(1.0)
            .sliderMax(1.0)
            .build()
    );

    public final Setting<Boolean> renderEsp = sgRender.add(new BoolSetting.Builder()
            .name("render-esp")
            .description("Подсвечивать блоки ESP")
            .defaultValue(true)
            .build()
    );

    public final Setting<Integer> renderDistance = sgRender.add(new IntSetting.Builder()
            .name("render-distance")
            .description("Дальность рендеринга (0 = без ограничений)")
            .defaultValue(0)
            .min(0)
            .max(128)
            .sliderMax(128)
            .build()
    );

    // --- ПРОДВИНУТЫЕ ---

    public final Setting<Boolean> chunkMode = sgAdvanced.add(new BoolSetting.Builder()
            .name("chunk-mode")
            .description("Сканировать по чанкам (эффективнее)")
            .defaultValue(true)
            .build()
    );

    public final Setting<Integer> maxResults = sgAdvanced.add(new IntSetting.Builder()
            .name("max-results")
            .description("Максимальное количество результатов")
            .defaultValue(100)
            .min(10)
            .max(500)
            .sliderMax(500)
            .build()
    );

    public final Setting<Boolean> sortByDistance = sgAdvanced.add(new BoolSetting.Builder()
            .name("sort-by-distance")
            .description("Сортировать по расстоянию")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> notifyFound = sgAdvanced.add(new BoolSetting.Builder()
            .name("notify-found")
            .description("Уведомлять о найденных блоках")
            .defaultValue(true)
            .build()
    );

    public final Setting<Integer> minBlocks = sgAdvanced.add(new IntSetting.Builder()
            .name("min-blocks")
            .description("Минимальное количество блоков для уведомления")
            .defaultValue(3)
            .min(1)
            .max(20)
            .sliderMax(20)
            .build()
    );

    // --- ПЕРЕМЕННЫЕ ---
    private List<BlockPos> foundBlocks = new ArrayList<>();
    private int scanCounter = 0;
    private boolean isScanning = false;
    private long lastScanTime = 0;

    // --- КОНСТРУКТОР ---
    public BaseFinder() {
        super(HM_CORE.CATEGORY, "BaseFinder", "Поиск баз по блокам");
    }

    @Override
    public void onActivate() {
        foundBlocks.clear();
        scanCounter = 0;
        isScanning = false;
        if (scanOnActivate.get()) {
            performScan();
        }
    }

    @Override
    public void onDeactivate() {
        foundBlocks.clear();
        isScanning = false;
    }

    // --- ТИК ---
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        scanCounter++;
        if (scanCounter >= scanDelay.get()) {
            scanCounter = 0;
            if (autoScan.get()) {
                performScan();
            }
        }
    }

    // --- ВЫПОЛНЕНИЕ СКАНА ---
    private void performScan() {
        if (mc.player == null || mc.world == null) return;
        if (isScanning) return;
        isScanning = true;

        foundBlocks.clear();
        BlockPos playerPos = mc.player.getBlockPos();
        int r = radius.get();

        if (chunkMode.get()) {
            // Сканирование по чанкам (эффективнее)
            int chunkRadius = r / 16;
            for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
                for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
                    int chunkX = (playerPos.getX() >> 4) + cx;
                    int chunkZ = (playerPos.getZ() >> 4) + cz;
                    // Проверяем, что чанк загружен
                    if (!mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) continue;
                    scanChunk(chunkX, chunkZ, playerPos);
                }
            }
        } else {
            // Сканирование блоками
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    for (int y = -10; y <= 10; y++) {
                        BlockPos pos = playerPos.add(x, y, z);
                        if (foundBlocks.size() >= maxResults.get()) break;
                        checkBlock(pos);
                    }
                }
            }
        }

        isScanning = false;

        // Сортировка по расстоянию
        if (sortByDistance.get()) {
            foundBlocks.sort(Comparator.comparingDouble(p -> mc.player.squaredDistanceTo(p.getX(), p.getY(), p.getZ())));
        }

        // Уведомление
        if (notifyFound.get() && foundBlocks.size() >= minBlocks.get()) {
            ChatUtils.info("§aНайдено блоков: " + foundBlocks.size() + " §7(радиус: " + radius.get() + ")");
            if (foundBlocks.size() > 50) {
                ChatUtils.info("§7Показаны первые 50 блоков из " + foundBlocks.size());
            }
        }
    }

    // --- СКАНИРОВАНИЕ ЧАНКА ---
    private void scanChunk(int chunkX, int chunkZ, BlockPos playerPos) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int r = radius.get();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = baseX + x;
                int worldZ = baseZ + z;
                // Проверка расстояния
                if (Math.abs(worldX - playerPos.getX()) > r) continue;
                if (Math.abs(worldZ - playerPos.getZ()) > r) continue;

                for (int y = -10; y <= 10; y++) {
                    if (foundBlocks.size() >= maxResults.get()) return;
                    BlockPos pos = new BlockPos(worldX, playerPos.getY() + y, worldZ);
                    checkBlock(pos);
                }
            }
        }
    }

    // --- ПРОВЕРКА БЛОКА ---
    private void checkBlock(BlockPos pos) {
        if (mc.world == null) return;
        BlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();

        if (chests.get() && block == Blocks.CHEST) {
            foundBlocks.add(pos);
            return;
        }
        if (chests.get() && block == Blocks.TRAPPED_CHEST) {
            foundBlocks.add(pos);
            return;
        }
        if (furnaces.get() && block == Blocks.FURNACE) {
            foundBlocks.add(pos);
            return;
        }
        if (furnaces.get() && block == Blocks.BLAST_FURNACE) {
            foundBlocks.add(pos);
            return;
        }
        if (furnaces.get() && block == Blocks.SMOKER) {
            foundBlocks.add(pos);
            return;
        }
        if (anvils.get() && (block == Blocks.ANVIL || block == Blocks.CHIPPED_ANVIL || block == Blocks.DAMAGED_ANVIL)) {
            foundBlocks.add(pos);
            return;
        }
        if (beds.get() && block == Blocks.RED_BED) {
            foundBlocks.add(pos);
            return;
        }
        if (portals.get() && block == Blocks.NETHER_PORTAL) {
            foundBlocks.add(pos);
            return;
        }
        if (craftTables.get() && block == Blocks.CRAFTING_TABLE) {
            foundBlocks.add(pos);
            return;
        }
        if (enchantingTables.get() && block == Blocks.ENCHANTING_TABLE) {
            foundBlocks.add(pos);
            return;
        }
        if (beacons.get() && block == Blocks.BEACON) {
            foundBlocks.add(pos);
            return;
        }
        if (shulkers.get() && block == Blocks.SHULKER_BOX) {
            foundBlocks.add(pos);
            return;
        }
        if (barrels.get() && block == Blocks.BARREL) {
            foundBlocks.add(pos);
        }
    }

    // --- РЕНДЕРИНГ ---
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || foundBlocks.isEmpty()) return;

        Color color = renderColor.get();
        int alpha = (int) (renderAlpha.get() * 255);
        Color c = new Color(color.r, color.g, color.b, alpha);

        for (BlockPos pos : foundBlocks) {
            if (renderDistance.get() > 0) {
                if (mc.player != null && mc.player.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ()) > renderDistance.get() * renderDistance.get()) {
                    continue;
                }
            }
            // Рендерим блок
            Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
            if (renderEsp.get()) {
                event.drawBox(box, c);
            }
            // Рисуем рамку
            event.drawBox(box, c, 2.0f);
        }
    }

    // --- ИНФОРМАЦИЯ В HUD ---
    @Override
    public String getInfoString() {
        if (!foundBlocks.isEmpty()) {
            return "§a" + foundBlocks.size() + " блоков";
        }
        return "§7Поиск...";
    }
}
