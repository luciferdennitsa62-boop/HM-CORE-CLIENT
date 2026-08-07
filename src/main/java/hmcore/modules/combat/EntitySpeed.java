package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import hmcore.HM_CORE;

public class EntitySpeed extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlayers = settings.createGroup("Players");
    private final SettingGroup sgMobs = settings.createGroup("Mobs");
    private final SettingGroup sgVehicles = settings.createGroup("Vehicles");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Режим ускорения")
            .defaultValue(Mode.Multiplier)
            .build()
    );

    public final Setting<Boolean> affectPlayers = sgGeneral.add(new BoolSetting.Builder()
            .name("affect-players")
            .description("Влиять на игроков (включая вас)")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> affectMobs = sgGeneral.add(new BoolSetting.Builder()
            .name("affect-mobs")
            .description("Влиять на мобов")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> affectAnimals = sgGeneral.add(new BoolSetting.Builder()
            .name("affect-animals")
            .description("Влиять на животных")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> affectVehicles = sgGeneral.add(new BoolSetting.Builder()
            .name("affect-vehicles")
            .description("Влиять на транспорт (лодки, тележки)")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> onlyWhenRiding = sgGeneral.add(new BoolSetting.Builder()
            .name("only-when-riding")
            .description("Только для сущности, на которой вы сидите")
            .defaultValue(false)
            .build()
    );

    // --- НАСТРОЙКИ ИГРОКОВ ---

    public final Setting<Double> playerSpeed = sgPlayers.add(new DoubleSetting.Builder()
            .name("player-speed")
            .description("Множитель скорости игроков (1.0 = норма)")
            .defaultValue(1.5)
            .min(0.1)
            .max(10.0)
            .sliderMax(10.0)
            .build()
    );

    public final Setting<Double> playerVerticalSpeed = sgPlayers.add(new DoubleSetting.Builder()
            .name("player-vertical-speed")
            .description("Вертикальный множитель для игроков")
            .defaultValue(1.0)
            .min(0.1)
            .max(10.0)
            .sliderMax(10.0)
            .build()
    );

    // --- НАСТРОЙКИ МОБОВ ---

    public final Setting<Double> mobSpeed = sgMobs.add(new DoubleSetting.Builder()
            .name("mob-speed")
            .description("Множитель скорости мобов")
            .defaultValue(1.5)
            .min(0.1)
            .max(10.0)
            .sliderMax(10.0)
            .build()
    );

    public final Setting<Double> animalSpeed = sgMobs.add(new DoubleSetting.Builder()
            .name("animal-speed")
            .description("Множитель скорости животных")
            .defaultValue(1.5)
            .min(0.1)
            .max(10.0)
            .sliderMax(10.0)
            .build()
    );

    // --- НАСТРОЙКИ ТРАНСПОРТА ---

    public final Setting<Double> boatSpeed = sgVehicles.add(new DoubleSetting.Builder()
            .name("boat-speed")
            .description("Множитель скорости лодок")
            .defaultValue(1.5)
            .min(0.1)
            .max(10.0)
            .sliderMax(10.0)
            .build()
    );

    public final Setting<Double> minecartSpeed = sgVehicles.add(new DoubleSetting.Builder()
            .name("minecart-speed")
            .description("Множитель скорости тележек")
            .defaultValue(1.5)
            .min(0.1)
            .max(10.0)
            .sliderMax(10.0)
            .build()
    );

    // --- ПРОДВИНУТЫЕ ---

    public final Setting<Boolean> onlyOnGround = sgAdvanced.add(new BoolSetting.Builder()
            .name("only-on-ground")
            .description("Только на земле (для мобов и животных)")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> resetVelocityOnDisable = sgAdvanced.add(new BoolSetting.Builder()
            .name("reset-velocity-on-disable")
            .description("Сбрасывать скорость при выключении")
            .defaultValue(true)
            .build()
    );

    // --- ENUM ---
    public enum Mode {
        Multiplier("Множитель"),
        Absolute("Абсолютное значение");

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
    public EntitySpeed() {
        super(HM_CORE.CATEGORY, "EntitySpeed", "Ускорение всех сущностей");
    }

    // --- ТИК ---
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (!shouldAffect(entity)) continue;

            double speedMultiplier = getSpeedMultiplier(entity);
            if (speedMultiplier == 1.0) continue;

            Vec3d velocity = entity.getVelocity();
            if (velocity == null) continue;

            // Применяем ускорение
            if (mode.get() == Mode.Multiplier) {
                entity.setVelocity(
                        velocity.x * speedMultiplier,
                        velocity.y * (entity instanceof PlayerEntity ? playerVerticalSpeed.get() : speedMultiplier),
                        velocity.z * speedMultiplier
                );
            } else {
                // Absolute mode: задаём скорость
                double targetSpeed = speedMultiplier * 0.3; // пример
                Vec3d direction = velocity.normalize();
                if (direction.length() > 0) {
                    entity.setVelocity(direction.multiply(targetSpeed));
                }
            }
        }
    }

    // --- ПРОВЕРКА, НУЖНО ЛИ ВЛИЯТЬ НА СУЩНОСТЬ ---
    private boolean shouldAffect(Entity entity) {
        if (entity == null) return false;

        // Проверка только верхом
        if (onlyWhenRiding.get()) {
            if (mc.player == null || mc.player.getVehicle() != entity) return false;
        }

        // Категории
        if (entity instanceof PlayerEntity) {
            return affectPlayers.get();
        }
        if (entity instanceof MobEntity && !(entity instanceof AnimalEntity)) {
            return affectMobs.get();
        }
        if (entity instanceof AnimalEntity) {
            return affectAnimals.get();
        }
        if (entity instanceof BoatEntity || entity instanceof MinecartEntity) {
            return affectVehicles.get();
        }
        return false;
    }

    // --- ОПРЕДЕЛЕНИЕ МНОЖИТЕЛЯ СКОРОСТИ ---
    private double getSpeedMultiplier(Entity entity) {
        if (entity instanceof PlayerEntity) {
            return playerSpeed.get();
        }
        if (entity instanceof AnimalEntity) {
            return animalSpeed.get();
        }
        if (entity instanceof MobEntity) {
            return mobSpeed.get();
        }
        if (entity instanceof BoatEntity) {
            return boatSpeed.get();
        }
        if (entity instanceof MinecartEntity) {
            return minecartSpeed.get();
        }
        return 1.0;
    }

    // --- СБРОС СКОРОСТИ ПРИ ВЫКЛЮЧЕНИИ ---
    @Override
    public void onDeactivate() {
        if (!resetVelocityOnDisable.get() || mc.world == null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (shouldAffect(entity)) {
                entity.setVelocity(0, 0, 0);
            }
        }
    }

    @Override
    public String getInfoString() {
        return "x" + String.format("%.1f", playerSpeed.get());
    }
}
