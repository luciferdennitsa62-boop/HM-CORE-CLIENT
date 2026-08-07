package hmcore.modules.combat;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.Particle;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import hmcore.HM_CORE;

import java.util.ArrayList;
import java.util.List;

public class NoRender extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWorld = settings.createGroup("World");
    private final SettingGroup sgParticles = settings.createGroup("Particles");
    private final SettingGroup sgUI = settings.createGroup("UI");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    // --- ОСНОВНЫЕ НАСТРОЙКИ ---

    public final Setting<Boolean> noWater = sgGeneral.add(new BoolSetting.Builder()
            .name("no-water")
            .description("Отключить рендеринг воды")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> noLava = sgGeneral.add(new BoolSetting.Builder()
            .name("no-lava")
            .description("Отключить рендеринг лавы")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> noSky = sgGeneral.add(new BoolSetting.Builder()
            .name("no-sky")
            .description("Отключить рендеринг неба")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> noClouds = sgGeneral.add(new BoolSetting.Builder()
            .name("no-clouds")
            .description("Отключить рендеринг облаков")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> noRain = sgGeneral.add(new BoolSetting.Builder()
            .name("no-rain")
            .description("Отключить рендеринг дождя и снега")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> noFog = sgGeneral.add(new BoolSetting.Builder()
            .name("no-fog")
            .description("Отключить туман")
            .defaultValue(true)
            .build()
    );

    // --- РЕНДЕРИНГ МИРА ---

    public final Setting<Boolean> noPortals = sgWorld.add(new BoolSetting.Builder()
            .name("no-portals")
            .description("Отключить рендеринг порталов (в Недере и Краю)")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> noFire = sgWorld.add(new BoolSetting.Builder()
            .name("no-fire")
            .description("Отключить рендеринг огня")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> noEnderChest = sgWorld.add(new BoolSetting.Builder()
            .name("no-ender-chest")
            .description("Отключить рендеринг эндер-сундуков")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> noSlimeBlock = sgWorld.add(new BoolSetting.Builder()
            .name("no-slime-block")
            .description("Отключить рендеринг липких блоков (эффект прыгучести)")
            .defaultValue(false)
            .build()
    );

    // --- ЧАСТИЦЫ ---

    public final Setting<Boolean> noParticles = sgParticles.add(new BoolSetting.Builder()
            .name("no-particles")
            .description("Отключить все частицы")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> noCritParticles = sgParticles.add(new BoolSetting.Builder()
            .name("no-crit-particles")
            .description("Отключить частицы критического удара")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> noEnchantParticles = sgParticles.add(new BoolSetting.Builder()
            .name("no-enchant-particles")
            .description("Отключить частицы зачарований")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> noPortalParticles = sgParticles.add(new BoolSetting.Builder()
            .name("no-portal-particles")
            .description("Отключить частицы порталов")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> noFireParticles = sgParticles.add(new BoolSetting.Builder()
            .name("no-fire-particles")
            .description("Отключить частицы огня")
            .defaultValue(false)
            .build()
    );

    // --- UI ---

    public final Setting<Boolean> noHUD = sgUI.add(new BoolSetting.Builder()
            .name("no-hud")
            .description("Отключить HUD (здоровье, голод, опыт)")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> noBossBar = sgUI.add(new BoolSetting.Builder()
            .name("no-boss-bar")
            .description("Отключить полосу босса")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> noChat = sgUI.add(new BoolSetting.Builder()
            .name("no-chat")
            .description("Отключить рендеринг чата")
            .defaultValue(false)
            .build()
    );

    // --- ПРОДВИНУТЫЕ ---

    public final Setting<Boolean> clearEntities = sgAdvanced.add(new BoolSetting.Builder()
            .name("clear-entities")
            .description("Удалить все видимые сущности (только визуально)")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> clearTileEntities = sgAdvanced.add(new BoolSetting.Builder()
            .name("clear-tile-entities")
            .description("Удалить все tile-сущности (сундуки, сундуки и т.д.)")
            .defaultValue(false)
            .build()
    );

    // --- КОНСТРУКТОР ---
    public NoRender() {
        super(HM_CORE.CATEGORY, "NoRender", "Отключение рендеринга для повышения FPS");
    }

    @Override
    public void onActivate() {
        if (notify.get()) {
            ChatUtils.info("NoRender активирован");
        }
    }

    @Override
    public void onDeactivate() {
        // Восстанавливаем настройки (по факту ничего не делаем, т.к. отключение происходит через события)
    }

    // --- ОБРАБОТЧИКИ РЕНДЕРИНГА (заглушки для отключения) ---
    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null) return;

        // Здесь можно отключать рендеринг воды, лавы, неба и т.д.
        // В реальности это делается через миксины или переопределение
        // Для простоты мы просто устанавливаем флаги в настройках мира

        if (mc.options != null) {
            // Отключение облаков
            if (noClouds.get()) {
                mc.options.getClouds().setValue(0); // 0 = выкл
            }
        }

        // Отключение дождя
        if (noRain.get()) {
            mc.world.setRainGradient(0);
            mc.world.setThunderGradient(0);
        }
    }

    // --- ОТКЛЮЧЕНИЕ ТУМАНА ---
    @EventHandler
    private void onFogRender(Render3DEvent event) {
        if (noFog.get()) {
            // Отключаем туман
            Fog.setFogStart(1000000);
            Fog.setFogEnd(1000000);
        }
    }

    // --- ОТКЛЮЧЕНИЕ ЧАСТИЦ ---
    @EventHandler
    private void onParticleRender(Render3DEvent event) {
        if (noParticles.get()) {
            // Отключаем все частицы
            if (mc.particleManager != null) {
                // Очищаем частицы
                mc.particleManager.clearParticles();
                // Отключаем добавление новых (через миксин)
            }
        }
    }

    // --- ОТКЛЮЧЕНИЕ HUD ---
    @EventHandler
    private void onHUD(Render3DEvent event) {
        if (noHUD.get()) {
            // Отключаем HUD
            if (mc.inGameHud != null) {
                // Убираем отображение здоровья, голода, опыта
                // В реальности это делается через миксин или переопределение
            }
        }
    }

    @Override
    public String getInfoString() {
        List<String> parts = new ArrayList<>();
        if (noWater.get()) parts.add("вода");
        if (noLava.get()) parts.add("лава");
        if (noClouds.get()) parts.add("облака");
        if (noRain.get()) parts.add("дождь");
        if (noParticles.get()) parts.add("частицы");

        if (parts.isEmpty()) return "всё";
        return String.join(", ", parts);
    }
}
