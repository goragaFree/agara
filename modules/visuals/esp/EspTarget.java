package moscow.rockstar.systems.modules.modules.visuals.esp;

import net.minecraft.entity.Entity;

public record EspTarget(Entity entity, EspTargetType type, EspPlayerType playerType, EspItemType itemType) {
}
