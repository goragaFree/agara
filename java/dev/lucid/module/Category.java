package dev.lucid.module;

/**
 * Группы модулей. Используются для сортировки в ClickGUI и HUD.
 */
public enum Category {
	RENDER("Render"),
	MOVEMENT("Movement"),
	COMBAT("Combat"),
	PLAYER("Player"),
	MISC("Misc");

	private final String displayName;

	Category(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return this.displayName;
	}
}
