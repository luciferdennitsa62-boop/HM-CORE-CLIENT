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
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import hmcore.HM_CORE;

public class AnchorAura extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlace = settings.createGroup("Place");
    private final SettingGroup sgTarget = settings.createGroup("Target");
    private final SettingGroup sgSafety = settings.createGroup("Safety");
    private final SettingGroup sgVisual = settings.createGroup("Visual");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
            .name("range")
            .description("Дальность действия")
            .defaultValue(5.0)
            .min(1.0)
            .max(8.0)
            .sliderMax(8.0)
            .build()
    );

    public final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
            .name("place-delay")
            .description("Задержка между установкой и взрывом (мс)")
            .defaultValue(500)
            .min(100)
            .max(2000)
            .sliderMax(2000)
            .build()
    );

    public final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
            .name("cooldown")
            .description("Задержка между атаками (мс)")
            .defaultValue(1000)
            .min(500)
            .max(5000)
            .sliderMax(5000)
            .build()
    );

    // --- УСТАНОВКА ---

    public final Setting<Boolean> autoPlace = sgPlace.add(new BoolSetting.Builder()
            .name("auto-place")
            .description("Автоматически ставить якорь")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> autoCharge = sgPlace.add(new BoolSetting.Builder()
            .name("auto-charge")
            .description("Автоматически заряжать якорь (нужен глоустоун)")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> autoBreak = sgPlace.add(new BoolSetting.Builder()
            .name("auto-break")
            .description("Автоматически взрывать якорь")
            .defaultValue(true)
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
            .description("Атаковать только игроков")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> antiFriend = sgTarget.add(new BoolSetting.Builder()
            .name("anti-friend")
            .description("Игнорировать друзей")
            .defaultValue(false)
            .build()
    );

    // --- БЕЗОПАСНОСТЬ ---

    public final Setting<Boolean> antiSuicide = sgSafety.add(new BoolSetting.Builder()
            .name("anti-suicide")
            .description("Не взрывать якорь, если вы в радиусе взрыва")
            .defaultValue(true)
            .build()
    );

    public final Setting<Double> selfDamageRange = sgSafety.add(new DoubleSetting.Builder()
            .name("self-damage-range")
            .description("Радиус, в котором взрыв может навредить вам")
            .defaultValue(3.0)
            .min(1.0)
            .max(6.0)
            .sliderMax(6.0)
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
            .description("Уведомлять об атаке")
            .defaultValue(false)
            .build()
    );

    // --- ПЕРЕМЕННЫЕ ---
    private long lastActionTime = 0;
    private BlockPos anchorPos = null;
    private PlayerEntity target = null;

    // --- КОНСТРУКТОР ---
    public AnchorAura() {
        super(HM_CORE.CATEGORY, "AnchorAura", "Автоматическая работа с Якорем Возрождения");
    }

    @Override
    public void onActivate() {
        lastActionTime = 0;
        anchorPos = null;
        target = null;
    }

    @Override
    public void onDeactivate() {
        anchorPos = null;
        target = null;
    }

    // --- ТИК ---
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Выбор цели
        target = (PlayerEntity) TargetUtils.getPlayerTarget(range.get(), targetPriority.get());
        if (target == null) return;

        // Проверка задержки
        if (System.currentTimeMillis() - lastActionTime < cooldown.get()) return;

        // Поиск места для якоря
        BlockPos placePos = findPlacePosition();
        if (placePos == null) return;

        // Проверка безопасности
        if (antiSuicide.get() && mc.player.distanceTo(Vec3d.ofCenter(placePos)) < selfDamageRange.get()) return;

        // --- УСТАНОВКА ---
        if (autoPlace.get()) {
            if (placeAnchor(placePos)) {
                anchorPos = placePos;
            } else {
                return;
            }
        }

        // --- ЗАРЯДКА ---
        if (autoCharge.get() && anchorPos != null) {
            if (!chargeAnchor(anchorPos)) {
                return;
            }
        }

        // --- ВЗРЫВ ---
        if (autoBreak.get() && anchorPos != null) {
            if (breakAnchor(anchorPos)) {
                lastActionTime = System.currentTimeMillis();
                if (notify.get()) {
                    info("§cЯкорь взорван! §7Цель: " + target.getName().getString());
                }
                anchorPos = null;
            }
        }
    }

    // --- ПОИСК МЕСТА ДЛЯ ЯКОРЯ ---
    private BlockPos findPlacePosition() {
        if (target == null) return null;

        // Ищем блок под ногами цели
        BlockPos targetPos = target.getBlockPos();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos pos = targetPos.add(x, -1, z);
                // Проверяем, что блок твёрдый
                if (!mc.world.getBlockState(pos).isAir()) {
                    BlockPos above = pos.up();
                    // Проверяем, что над ним воздух
                    if (mc.world.getBlockState(above).isAir()) {
                        return above;
                    }
                }
            }
        }
        return null;
    }

    // --- УСТАНОВКА ЯКОРЯ ---
    private boolean placeAnchor(BlockPos pos) {
        if (!InvUtils.findInHotbar(Items.RESPAWN_ANCHOR).found()) return false;
        BlockUtils.place(pos, InvUtils.findInHotbar(Items.RESPAWN_ANCHOR), true, 0);
        return true;
    }

    // --- ЗАРЯДКА ЯКОРЯ ---
    private boolean chargeAnchor(BlockPos pos) {
        if (!InvUtils.findInHotbar(Items.GLOWSTONE).found()) return false;
        // Используем глоустоун на якоре
        BlockUtils.use(pos, InvUtils.findInHotbar(Items.GLOWSTONE));
        return true;
    }

    // --- ВЗРЫВ ЯКОРЯ ---
    private boolean breakAnchor(BlockPos pos) {
        // Клик ПКМ по якорю, чтобы взорвать
        BlockHitResult hit = new BlockHitResult(
                Vec3d.ofCenter(pos),
                Direction.UP,
                pos,
                false
        );
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        return true;
    }

    @Override
    public String getInfoString() {
        if (target != null) {
            return "§c" + target.getName().getString();
        }
        return "§7Ожидание...";
    }
}
