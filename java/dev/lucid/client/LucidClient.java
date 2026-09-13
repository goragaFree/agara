package dev.lucid.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import dev.lucid.event.EventBus;
import dev.lucid.module.ModuleManager;
import dev.lucid.module.impl.render.Removals;
import dev.lucid.util.config.ConfigManager;
import dev.lucid.util.render.ClientPipelines;
import dev.lucid.util.render.Render3D;
import dev.lucid.util.render.SaturationRenderer;

import static dev.lucid.Lucid.LOGGER;

public class LucidClient implements ClientModInitializer {

    /**
     * Шина событий. Создаётся раньше менеджера модулей специально: модули подписываются
     * при включении, а включаться они могут уже во время {@code MODULES.init()}.
     * Порядок инициализации статических полей в Java — сверху вниз, поэтому менять их
     * местами нельзя: EVENTS окажется null в самый неудобный момент.
     */
    public static final EventBus EVENTS = new EventBus();

    /**
     * Конфиг тоже раньше менеджера: {@code MODULES.init()} в конце регистрации сам читает
     * файл, а настройки во время чтения дёргают {@link ConfigManager#markDirty()}.
     */
    public static final ConfigManager CONFIG = new ConfigManager();

    public static final ModuleManager MODULES = new ModuleManager();

    @Override
    public void onInitializeClient() {
        // Пайплайны регистрируются до первой отрисовки, иначе порядок будет плавать.
        ClientPipelines.bootstrap();

        // Подписка на отрисовку мира до модулей: они регистрируют в ней свои рендеры.
        Render3D.init();
        SaturationRenderer.getInstance().init();

        MODULES.init();
        this.registerHudHides();
        LOGGER.info("Lucid initialized");
    }

    /**
     * Ванильный слой {@code MOB_EFFECTS} — те квадратики зелий справа сверху.
     * Подменяем его: если чип включён, слой просто ничего не рисует.
     */
    private void registerHudHides() {
        HudElementRegistry.replaceElement(VanillaHudElements.MOB_EFFECTS, original -> (graphics, deltaTracker) -> {
            if (Removals.hideEffectIcons()) {
                return;
            }

            original.extractRenderState(graphics, deltaTracker);
        });
    }
}
