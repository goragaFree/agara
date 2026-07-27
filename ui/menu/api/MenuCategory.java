package moscow.rockstar.ui.menu.api;

import lombok.Generated;
import moscow.rockstar.Rockstar;
import moscow.rockstar.systems.modules.api.ModuleCategory;
import moscow.rockstar.utility.render.obj.CustomSprite;
import moscow.rockstar.utility.render.penis.PenisPlayer;

public enum MenuCategory {
   COMBAT("Combat", ModuleCategory.COMBAT, CustomSprite.COMBAT, CustomSprite.BIG_COMBAT),
   MOVEMENT("Movement", ModuleCategory.MOVEMENT, CustomSprite.MOVEMENT, CustomSprite.BIG_MOVEMENT),
   VISUALS("Visuals", ModuleCategory.VISUALS, CustomSprite.VISUALS, CustomSprite.BIG_VISUALS),
   PLAYER("Player", ModuleCategory.PLAYER, CustomSprite.PLAYER, CustomSprite.BIG_PLAYER),
   OTHER("Other", ModuleCategory.OTHER, CustomSprite.OTHER, CustomSprite.BIG_OTHER);

   private final String name;
   private final ModuleCategory category;
   private final CustomSprite menuSprite;
   private final CustomSprite bigMenuSprite;
   private PenisPlayer penisPlayer;

   public void initPenis() {
      try {
         this.penisPlayer = new PenisPlayer(Rockstar.id("penises/" + this.name.toLowerCase() + ".penis"));
         this.penisPlayer.stop();
      } catch (RuntimeException e) {
         this.penisPlayer = null;
      }
   }

   public void playOnce() {
      if (this.penisPlayer != null) {
         this.penisPlayer.playOnce();
      }
   }

   public void stopPenis() {
      if (this.penisPlayer != null) {
         this.penisPlayer.stop();
      }
   }

   public PenisPlayer getPenisPlayer() {
      return this.penisPlayer;
   }

   @Generated
   public String getName() {
      return this.name;
   }

   @Generated
   public ModuleCategory getCategory() {
      return this.category;
   }

   @Generated
   public CustomSprite getMenuSprite() {
      return this.menuSprite;
   }

   @Generated
   public CustomSprite getBigMenuSprite() {
      return this.bigMenuSprite;
   }

   @Generated
   MenuCategory(String name, ModuleCategory category, CustomSprite menuSprite, CustomSprite bigMenuSprite) {
      this.name = name;
      this.category = category;
      this.menuSprite = menuSprite;
      this.bigMenuSprite = bigMenuSprite;
   }
}
