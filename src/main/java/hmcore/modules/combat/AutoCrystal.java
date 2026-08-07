package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import hmcore.HM_CORE;

import java.util.ArrayList;
import java.util.List;

public class AutoCrystal extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlace = settings.createGroup("Place");
    private final SettingGroup sgBreak = settings.createGroup("Break");
    private final SettingGroup sgTarget = settings.createGroup("Target");
    private final SettingGroup sgSafety = settings.createGroup("Safety");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Boolean> place = sgGeneral.add(new BoolSetting.Builder()
            .name("place")
            .description("Ставить кристаллы")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> breakCrystals = sgGeneral.add(new BoolSetting.Builder()
            .name("break")
            .description("Взрывать кристаллы")
            .defaultValue(true)
            .build()
    );

    public final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
            .name("range")
            .description("Максимальная дистанция до цели и кристаллов")
            .defaultValue(5.0)
            .min(1.0)
            .max(8.0)
            .sliderMax(8.0)
            .build()
    );

    public final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
            .name("place-delay")
            .description("Задержка между установками (мс)")
            .defaultValue(50)
            .min(0)
            .max(500)
            .sliderMax(500)
            .build()
    );

    public final Setting<Integer> breakDelay = sgGeneral.add(new IntSetting.Builder()
            .name("break-delay")
            .description("Задержка между взрывами (мс)")
            .defaultValue(50)
            .min(0)
            .max(500)
            .sliderMax(500)
            .build()
    );

    // --- УСТАНОВКА КРИСТАЛЛОВ ---

    public final Setting<PlaceMode> placeMode = sgPlace.add(new EnumSetting.Builder<PlaceMode>()
            .name("place-mode")
            .description("Режим установки")
            .defaultValue(PlaceMode.Vanilla)
            .build()
    );

    public final Setting<Integer> placeRadius = sgPlace.add(new IntSetting.Builder()
            .name("place-radius")
            .description("Радиус поиска блоков для установки")
            .defaultValue(3)
            .min(1)
            .max(6)
            .sliderMax(6)
            .build()
    );

    public final Setting<Boolean> multiPlace = sgPlace.add(new BoolSetting.Builder()
            .name("multi-place")
            .description("Ставить по несколько кристаллов за раз")
            .defaultValue(false)
            .build()
    );

    public final Setting<Integer> multiPlaceCount = sgPlace.add(new IntSetting.Builder()
            .name("multi-place-count")
            .description("Количество кристаллов за раз (при multi-place)")
            .defaultValue(2)
            .min(1)
            .max(5)
            .sliderMax(5)
            .build()
    );

    // --- ВЗРЫВ КРИСТАЛЛОВ ---

    public final Setting<BreakMode> breakMode = sgBreak.add(new EnumSetting.Builder<BreakMode>()
            .name("break-mode")
            .description("Режим взрыва")
            .defaultValue(BreakMode.Swing)
            .build()
    );

    public final Setting<Boolean> breakOwn = sgBreak.add(new BoolSetting.Builder()
            .name("break-own")
            .description("Взрывать свои кристаллы (для самоповреждения или если враг рядом)")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> onlyBreakWhenTarget = sgBreak.add(new BoolSetting.Builder()
            .name("only-break-when-target")
            .description("Взрывать кристаллы только если рядом есть цель")
            .defaultValue(true)
            .build()
    );

    // --- ЦЕЛЬ ---

    public final Setting<SortPriority> targetPriority = sgTarget.add(new EnumSetting.Builder<SortPriority>()
            .name("target-priority")
            .description("Приоритет выбора цели")
            .defaultValue(SortPriority.LowestHealth)
            .build()
    );

    public final Setting<Double> targetRange = sgTarget.add(new DoubleSetting.Builder()
            .name("target-range")
            .description("Дистанция до цели")
            .defaultValue(5.0)
            .min(1.0)
            .max(8.0)
            .sliderMax(8.0)
            .build()
    );

    public final Setting<Boolean> antiFriend = sgTarget.add(new BoolSetting.Builder()
            .name("anti-friend")
            .description("Игнорировать друзей")
            .defaultValue(true)
            .build()
    );

    // --- БЕЗОПАСНОСТЬ ---

    public final Setting<Boolean> antiSuicide = sgSafety.add(new BoolSetting.Builder()
            .name("anti-suicide")
            .description("Не ставить кристаллы, если они могут убить вас")
            .defaultValue(true)
            .build()
    );

    public final Setting<Double> minSelfDistance = sgSafety.add(new DoubleSetting.Builder()
            .name("min-self-distance")
            .description("Минимальное расстояние от себя до кристалла")
            .defaultValue(2.0)
            .min(1.0)
            .max(4.0)
            .sliderMax(4.0)
            .build()
    );

    // --- ВИЗУАЛ ---

    public final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
            .name("render")
            .description("Показывать цели и кристаллы")
            .defaultValue(true)
            .build()
    );

    // --- ВНУТРЕННИЕ ПЕРЕМЕННЫЕ ---

    private long lastPlaceTime = 0;
    private long lastBreakTime = 0;
    private PlayerEntity target;
    private final List<BlockPos> placedBlocks = new ArrayList<>();
    private final List<EndCrystalEntity> breakCrystalList = new ArrayList<>();

    // --- ENUM'Ы ---

    public enum PlaceMode {
        Vanilla, Packet, Air
    }

    public enum BreakMode {
        Swing, Packet
    }

    // --- КОНСТРУКТОР ---

    public AutoCrystal() {
        super(HM_CORE.CATEGORY, "AutoCrystal", "Автоматическая установка и взрыв кристаллов Энда");
    }

    @Override
    public void onActivate() {
        placedBlocks.clear();
        breakCrystalList.clear();
    }

    @Override
    public void onDeactivate() {
        placedBlocks.clear();
        breakCrystalList.clear();
    }

    // --- ОСНОВНОЙ ТИК ---

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Выбор цели
        target = (PlayerEntity) TargetUtils.getPlayerTarget(targetRange.get(), targetPriority.get());
        if (target == null && onlyBreakWhenTarget.get()) return;

        // --- BREAK (взрыв кристаллов) ---
        if (breakCrystals.get()) {
            breakCrystals();
        }

        // --- PLACE (установка) ---
        if (place.get()) {
            placeCrystals();
        }
    }

    // --- ВЗРЫВ КРИСТАЛЛОВ ---

    private void breakCrystals() {
        if (System.currentTimeMillis() - lastBreakTime < breakDelay.get()) return;

        // Ищем кристаллы в радиусе
        List<EndCrystalEntity> crystals = new ArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;
            if (!breakOwn.get() && crystal.getUuid().equals(mc.player.getUuid())) continue; // свой кристалл?
            // Проверка дистанции
            if (mc.player.distanceTo(crystal) > range.get()) continue;
            // Проверка, находится ли кристалл рядом с целью (если включено)
            if (onlyBreakWhenTarget.get() && target != null) {
                if (crystal.distanceTo(target) > 3.0) continue;
            }
            crystals.add(crystal);
        }

        if (crystals.isEmpty()) return;

        // Берём ближайший кристалл
        crystals.sort((a, b) -> Double.compare(mc.player.distanceTo(a), mc.player.distanceTo(b)));
        EndCrystalEntity crystal = crystals.get(0);

        // Взрываем
        if (breakMode.get() == BreakMode.Swing) {
            mc.interactionManager.attackEntity(mc.player, crystal);
            mc.player.swingHand(Hand.MAIN_HAND);
        } else {
            // Packet mode
            mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, mc.player.isSneaking()));
            mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }

        lastBreakTime = System.currentTimeMillis();
    }

    // --- УСТАНОВКА КРИСТАЛЛОВ ---

    private void placeCrystals() {
        if (System.currentTimeMillis() - lastPlaceTime < placeDelay.get()) return;

        // Находим блоки для установки (обсидиан/бедрок)
        List<BlockPos> possiblePositions = new ArrayList<>();
        BlockPos playerPos = mc.player.getBlockPos();

        for (int x = -placeRadius.get(); x <= placeRadius.get(); x++) {
            for (int z = -placeRadius.get(); z <= placeRadius.get(); z++) {
                for (int y = -1; y <= 1; y++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    // Проверяем, что блок подходит (обсидиан или бедрок)
                    if (!mc.world.getBlockState(pos).isOf(Blocks.OBSIDIAN) && !mc.world.getBlockState(pos).isOf(Blocks.BEDROCK))
                        continue;
                    // Проверяем, что над блоком есть воздух
                    BlockPos above = pos.up();
                    if (!mc.world.getBlockState(above).isAir()) continue;
                    // Проверяем, что над этим местом нет сущностей (кристаллов)
                    if (mc.world.getBlockState(above.up()).isAir()) {
                        possiblePositions.add(above);
                    }
                }
            }
        }

        if (possiblePositions.isEmpty()) return;

        // Фильтруем по расстоянию до себя (безопасность)
        if (antiSuicide.get()) {
            possiblePositions.removeIf(pos -> mc.player.distanceTo(Vec3d.ofCenter(pos)) < minSelfDistance.get());
        }

        // Если цель есть, сортируем по близости к ней
        if (target != null) {
            possiblePositions.sort((a, b) -> Double.compare(
                    target.distanceTo(Vec3d.ofCenter(a)),
                    target.distanceTo(Vec3d.ofCenter(b))
            ));
        } else {
            possiblePositions.sort((a, b) -> Double.compare(
                    mc.player.distanceTo(Vec3d.ofCenter(a)),
                    mc.player.distanceTo(Vec3d.ofCenter(b))
            ));
        }

        // Берём первый подходящий
        BlockPos pos = possiblePositions.get(0);

        // Установка кристалла
        placeCrystal(pos);

        // Мультиустановка
        if (multiPlace.get()) {
            for (int i = 1; i < Math.min(multiPlaceCount.get(), possiblePositions.size()); i++) {
                placeCrystal(possiblePositions.get(i));
            }
        }

        lastPlaceTime = System.currentTimeMillis();
    }

    private void placeCrystal(BlockPos pos) {
        if (placeMode.get() == PlaceMode.Vanilla) {
            // Обычная установка через взаимодействие
            BlockUtils.place(pos.down(), InvUtils.findInHotbar(Items.END_CRYSTAL), false, 0);
        } else if (placeMode.get() == PlaceMode.Packet) {
            // Установка через пакеты (без анимации)
            FindItemResult crystal = InvUtils.findInHotbar(Items.END_CRYSTAL);
            if (!crystal.found()) return;
            int oldSlot = mc.player.getInventory().selectedSlot;
            InvUtils.swap(crystal.slot(), true);

            mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
                    Hand.MAIN_HAND,
                    new BlockHitResult(
                            Vec3d.ofCenter(pos.down()),
                            Direction.UP,
                            pos.down(),
                            false
                    )
            ));
            mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            InvUtils.swap(oldSlot, true);
        } else {
            // AirPlace — ставим в воздухе (не работает на многих серверах)
            // Просто пробуем поставить
            BlockUtils.place(pos.down(), InvUtils.findInHotbar(Items.END_CRYSTAL), false, 0);
        }
    }

    // --- РЕНДЕРИНГ (заглушка) ---

    @EventHandler
    private void onTickRender(TickEvent.Post event) {
        if (render.get() && mc.world != null) {
            // Здесь можно рисовать круги/маркеры
        }
    }

    // --- ОБРАБОТЧИКИ СОБЫТИЙ (для очистки списков) ---

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        // Ничего
    }

    @EventHandler
    private void onEntityRemoved(EntityRemovedEvent event) {
        // Ничего
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        placedBlocks.clear();
        breakCrystalList.clear();
    }
}
