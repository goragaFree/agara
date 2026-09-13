package dev.lucid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.GameRenderer;

import dev.lucid.client.LucidClient;
import dev.lucid.util.render.Render3D;
import dev.lucid.util.render.SaturationRenderer;

/**
 * Закрытие рендерера — последняя точка, где клиент ещё жив и владеет своими ресурсами.
 *
 * <p>Здесь две разные вещи: дописать несохранённые настройки и отдать буфер вершин мирового
 * рендера. Интерфейс закрывать не нужно — его буферами и текстурами управляет сама игра.</p>
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "close", at = @At("RETURN"))
    private void lucid$onGameRendererClose(CallbackInfo ci) {
        // Игра закрывается — последняя возможность записать несохранённое.
        LucidClient.CONFIG.saveIfDirty(LucidClient.MODULES);

        Render3D.close();
        SaturationRenderer.getInstance().close();
    }
}
