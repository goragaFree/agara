package moscow.rockstar.systems.modules.modules.visuals.esp;

public enum EspItemType {
   HELD("held", "esp.targets.items.held"),
   DROPPED("dropped", "esp.targets.items.dropped");

   private final String id;
   private final String translationKey;

   EspItemType(String id, String translationKey) {
      this.id = id;
      this.translationKey = translationKey;
   }

   public static EspItemType byId(String id) {
      for (EspItemType type : values()) {
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
}
