package dev.lucid.module.impl.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

import dev.lucid.module.Category;
import dev.lucid.module.Module;

/**
 * Автоматически держит клавишу спринта, пока игрок идёт вперёд.
 *
 * <p>Мы не вызываем {@code setSprinting} напрямую, а жмём ванильную клавишу спринта.
 * Так все проверки игры — голод, ползание, еда и щит в руках, упёрся в стену — остаются
 * работать сами, и сервер видит ровно то же, что при обычном беге.</p>
 */
public class AutoSprint extends Module {

    public AutoSprint() {
        super("auto_sprint", "AutoSprint", Category.MOVEMENT,
                "Автоматически бежит при движении вперёд");
    }

    @Override
    public void onTick(Minecraft client) {
        Options options = client.options;

        // Спринт только вперёд и не в крадущемся режиме — иначе слетишь с края блока.
        boolean shouldSprint = options.keyUp.isDown() && !options.keyShift.isDown();

        options.keySprint.setDown(shouldSprint);
    }

    @Override
    protected void onDisable() {
        // Отпускаем клавишу, иначе она останется зажатой после выключения модуля.
        mc().options.keySprint.setDown(false);
    }
}
