package dev.lucid.module.impl.movement;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractCommandBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.entity.player.Input;

import dev.lucid.client.LucidClient;
import dev.lucid.event.impl.PacketEvent;
import dev.lucid.screen.gui.ClickGuiScreen;
import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.BooleanSetting;
import dev.lucid.setting.ModeSetting;
import dev.lucid.util.move.MovementKeys;

/**
 * Ходьба в инвентаре по схеме Blade 26.2.
 *
 * <p>Движение пишется в {@code KeyboardInput} после ванильного tick.
 * Пакеты трогаем только в Legit и только исходящие клики/закрытие своего
 * инвентаря. Входящие не перехватываем.</p>
 */
public class InventoryMove extends Module {

	private static final String MODE_DEFAULT = "Default";
	private static final String MODE_LEGIT = "Legit";

	private final ModeSetting mode = this.addSetting(new ModeSetting(
			"mode", "Режим",
			"Default — только ходьба. Legit — клики не уходят, пока бежишь",
			MODE_DEFAULT, List.of(MODE_DEFAULT, MODE_LEGIT)));

	private final BooleanSetting grimBypass = this.addSetting(new BooleanSetting(
			"grim_bypass", "Grim",
			"Перед закрытием инвентаря останавливаемся, потом отправляем клики",
			true).visibleWhen(() -> this.mode.is(MODE_LEGIT)));

	private final List<Packet<?>> delayed = new CopyOnWriteArrayList<>();
	private boolean processing;
	private boolean movedInGui;
	private boolean movementLocked;
	private int scriptStep = -1;
	private int stepTimer;

	public InventoryMove() {
		super("inventory_move", "InventoryMove", Category.MOVEMENT,
				"Ходьба в инвентаре");

		this.listen(PacketEvent.class, this::onPacketSend);
	}

	public static InventoryMove getEnabled() {
		try {
			InventoryMove module = LucidClient.MODULES.get(InventoryMove.class);
			return module.isEnabled() ? module : null;
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	public static Input screenInput() {
		InventoryMove module = getEnabled();
		Minecraft client = Minecraft.getInstance();

		if (client.player == null || module == null || module.movementLocked || module.processing) {
			return null;
		}

		Screen screen = client.gui.screen();

		if (!module.canWalk(screen)) {
			return null;
		}

		return new Input(
				MovementKeys.physicalDown(client.options.keyUp),
				MovementKeys.physicalDown(client.options.keyDown),
				MovementKeys.physicalDown(client.options.keyLeft),
				MovementKeys.physicalDown(client.options.keyRight),
				MovementKeys.physicalDown(client.options.keyJump),
				MovementKeys.physicalDown(client.options.keyShift),
				MovementKeys.physicalDown(client.options.keySprint));
	}

	public static boolean shouldStopMovement() {
		InventoryMove module = getEnabled();
		return module != null && (module.movementLocked || module.processing);
	}

	@Override
	protected void onDisable() {
		this.flushNow();
		this.cleanup();
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null) {
			this.cleanup();
			return;
		}

		if (this.processing) {
			this.tickScript(client.player);
		}

		if (this.canWalk(client.gui.screen()) && this.movementKeysDown() && !this.delayed.isEmpty()) {
			this.movedInGui = true;
		}
	}

	private void onPacketSend(PacketEvent event) {
		if (this.processing || !this.mode.is(MODE_LEGIT) || mc().player == null) {
			return;
		}

		Packet<?> packet = event.getPacket();
		boolean moving = this.movedInGui || this.movementKeysDown();
		Screen screen = mc().gui.screen();

		if (packet instanceof ServerboundContainerClickPacket
				&& screen instanceof InventoryScreen
				&& moving) {
			this.delayed.add(packet);
			this.movedInGui = true;
			event.cancel();
			return;
		}

		if (packet instanceof ServerboundContainerClosePacket close
				&& close.getContainerId() == 0
				&& moving) {
			if (!this.grimBypass.get()) {
				return;
			}

			event.cancel();

			if (!this.delayed.isEmpty()) {
				this.delayed.add(close);
				this.startScript();
			}
		}
	}

	private void startScript() {
		this.processing = true;
		this.scriptStep = 0;
		this.stepTimer = 0;
	}

	private void tickScript(LocalPlayer player) {
		if (player.connection == null) {
			this.cleanup();
			return;
		}

		switch (this.scriptStep) {
			case 0 -> {
				this.movementLocked = true;
				this.scriptStep++;
				this.stepTimer = 0;
			}
			case 1, 2 -> {
				this.stepTimer++;

				if (this.stepTimer >= 2) {
					this.scriptStep++;
					this.stepTimer = 0;
				}
			}
			case 3 -> {
				for (Packet<?> packet : this.delayed) {
					if (!(packet instanceof ServerboundContainerClosePacket)) {
						player.connection.send(packet);
					}
				}

				this.scriptStep++;
				this.stepTimer = 0;
			}
			case 4, 5 -> {
				this.stepTimer++;

				if (this.stepTimer >= 2) {
					this.scriptStep++;
					this.stepTimer = 0;
				}
			}
			case 6 -> {
				for (Packet<?> packet : this.delayed) {
					if (packet instanceof ServerboundContainerClosePacket) {
						player.connection.send(packet);
					}
				}

				this.delayed.clear();
				this.scriptStep++;
			}
			case 7 -> {
				this.cleanup();
			}
		}
	}

	private boolean movementKeysDown() {
		Minecraft client = mc();

		if (client.getWindow() == null || client.options == null) {
			return false;
		}

		KeyMapping[] keys = {
				client.options.keyUp,
				client.options.keyDown,
				client.options.keyLeft,
				client.options.keyRight,
				client.options.keyJump
		};

		for (KeyMapping key : keys) {
			if (MovementKeys.physicalDown(key)) {
				return true;
			}
		}

		return false;
	}

	private boolean canWalk(Screen screen) {
		if (screen == null) {
			return false;
		}

		if (screen instanceof ClickGuiScreen
				|| screen instanceof InventoryScreen
				|| screen instanceof CreativeModeInventoryScreen) {
			return true;
		}

		return !(screen instanceof ChatScreen)
				&& !(screen instanceof AbstractSignEditScreen)
				&& !(screen instanceof AnvilScreen)
				&& !(screen instanceof AbstractCommandBlockEditScreen)
				&& !(screen instanceof StructureBlockEditScreen)
				&& !(screen instanceof BookEditScreen);
	}

	private void flushNow() {
		LocalPlayer player = mc().player;

		if (player == null || player.connection == null) {
			return;
		}

		for (Packet<?> packet : this.delayed) {
			player.connection.send(packet);
		}
	}

	private void cleanup() {
		this.delayed.clear();
		this.processing = false;
		this.movedInGui = false;
		this.movementLocked = false;
		this.scriptStep = -1;
		this.stepTimer = 0;
	}
}
