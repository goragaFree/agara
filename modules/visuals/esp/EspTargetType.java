package moscow.rockstar.systems.modules.modules.visuals.esp;

public enum EspTargetType {
   PLAYERS("players", "esp.targets.players", true),
   MOBS("mobs", "esp.targets.mobs", false),
   ANIMALS("animals", "esp.targets.animals", false),
   ITEMS("items", "esp.targets.items", true);

   private final String id;
   private final String translationKey;
   private final boolean hasSubTargets;

   EspTargetType(String id, String translationKey, boolean hasSubTargets) {
      this.id = id;
      this.translationKey = translationKey;
      this.hasSubTargets = hasSubTargets;
   }

   public static EspTargetType byId(String id) {
      for (EspTargetType type : values()) {
         if (type.id.equals(id)) {
            return type;
         }
      }

      return null;
   }

   public String id() {
      return this.id;
   }

   public String translationKey() {
      return this.translationKey;
   }

   public boolean hasSubTargets() {
      return this.hasSubTargets;
   }
}
