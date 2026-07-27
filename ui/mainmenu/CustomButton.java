package moscow.rockstar.ui.mainmenu;

import lombok.Generated;
import moscow.rockstar.Rockstar;
import moscow.rockstar.framework.base.UIContext;
import moscow.rockstar.framework.objects.BorderRadius;
import moscow.rockstar.systems.modules.modules.visuals.Interface;
import moscow.rockstar.utility.animation.base.Animation;
import moscow.rockstar.utility.animation.base.Easing;
import moscow.rockstar.utility.colors.ColorRGBA;
import moscow.rockstar.utility.game.cursor.CursorType;
import moscow.rockstar.utility.game.cursor.CursorUtility;
import moscow.rockstar.utility.interfaces.IMinecraft;
import moscow.rockstar.utility.render.obj.Rect;

public class CustomButton extends Rect {
   private final String icon;
   private final float iconSize;
   private final Runnable onClick;
   private final ColorRGBA backgroundColor = new ColorRGBA(58.0F, 58.0F, 58.0F);
   private final Animation activeAnim = new Animation(400L, 0.0F, Easing.BAKEK);
   private final Animation hoverAnim = new Animation(300L, 0.0F, Easing.FIGMA_EASE_IN_OUT);

   public void draw(UIContext context) {
      if (this.hovered(context.getMouseX(), context.getMouseY()) && this.activeAnim.getValue() == 1.0F) {
         CursorUtility.set(CursorType.HAND);
      }

      this.hoverAnim.update(this.hovered(context.getMouseX(), context.getMouseY()) && this.activeAnim.getValue() == 1.0F);
      if (IMinecraft.mc.world == null) {
         Rockstar.getInstance().getModuleManager().getModule(Interface.class).getLiquidGlassAnim().update(Interface.glassSelected());
      }

      float alpha = this.activeAnim.getValue();
      float hover = this.hoverAnim.getValue();
      float radius = Math.min(this.width, this.height) / 2.0F;
      BorderRadius br = BorderRadius.all(radius);
      if (Interface.showGlass()) {
         context.drawLiquidGlass(
            this.x - 1.0F, this.y - 1.0F, this.width + 2.0F, this.height + 2.0F, 7.0F, 0.08F, br, ColorRGBA.WHITE.withAlpha(255.0F * alpha)
         );
         context.drawRoundedRect(this.x, this.y, this.width, this.height, br, this.backgroundColor.withAlpha(255.0F * (0.15F * alpha + 0.15F * hover)));
      } else {
         context.drawRoundedRect(this.x, this.y, this.width, this.height, br, this.backgroundColor.withAlpha(255.0F * (0.33F * alpha + 0.2F * hover)));
      }

      context.drawTexture(
         Rockstar.id(this.icon),
         this.x + (this.width - this.iconSize) / 2.0F,
         this.y + (this.height - this.iconSize) / 2.0F,
         this.iconSize,
         this.iconSize,
         ColorRGBA.WHITE.withAlpha(255.0F * alpha)
      );
   }

   public void click(double mouseX, double mouseY, int button) {
      if (this.hovered(mouseX, mouseY) && button == 0 && this.activeAnim.getValue() == 1.0F) {
         this.onClick.run();
      }
   }

   @Generated
   public CustomButton(String icon, float iconSize, Runnable onClick) {
      this.icon = icon;
      this.iconSize = iconSize;
      this.onClick = onClick;
   }

   @Generated
   public Animation getActiveAnim() {
      return this.activeAnim;
   }
}
