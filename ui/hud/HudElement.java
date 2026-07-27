package moscow.rockstar.ui.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import lombok.Generated;
import moscow.rockstar.Rockstar;
import moscow.rockstar.framework.base.UIContext;
import moscow.rockstar.framework.objects.MouseButton;
import moscow.rockstar.systems.localization.Localizator;
import moscow.rockstar.systems.setting.Setting;
import moscow.rockstar.systems.setting.SettingsContainer;
import moscow.rockstar.ui.components.popup.Popup;
import moscow.rockstar.utility.animation.base.Animation;
import moscow.rockstar.utility.animation.base.Easing;
import moscow.rockstar.utility.game.cursor.CursorType;
import moscow.rockstar.utility.game.cursor.CursorUtility;
import moscow.rockstar.utility.gui.GuiUtility;
import moscow.rockstar.utility.interfaces.IMinecraft;
import moscow.rockstar.utility.interfaces.IScaledResolution;
import moscow.rockstar.utility.render.RenderUtility;

public abstract class HudElement implements SettingsContainer, IMinecraft {
   protected float x;
   protected float y;
   protected float width;
   protected float height;
   protected final Animation animation = new Animation(300L, 0.0F, Easing.BAKEK_SIZE);
   protected final Animation visible = new Animation(300L, 0.0F, Easing.BAKEK_SIZE);
   protected final Animation selecting = new Animation(300L, 0.0F, Easing.BAKEK_SIZE);
   protected final Animation dragAnim = new Animation(300L, 0.0F, Easing.FIGMA_EASE_IN_OUT);
   private final Animation blurAnim = new Animation(300L, 0.0F, Easing.FIGMA_EASE_IN_OUT);
   private final Animation loadingAnim = new Animation(700L, 0.0F, Easing.SMOOTH_STEP);
   private final Animation widthAnim = new Animation(300L, 0.0F, Easing.BAKEK_SIZE);
   private final Animation heightAnim = new Animation(300L, 0.0F, Easing.BAKEK_SIZE);
   protected boolean showing;
   protected boolean select;
   private List<Setting> settings = new ArrayList<>();
   private boolean dragging;
   private float dragX;
   private float dragY;
   private float startDragX;
   private float startDragY;
   protected final String name;
   protected final String icon;

   public HudElement(String name, String icon) {
      this.name = name;
      this.icon = icon;
   }

   public void render(UIContext context) {
      this.update(context);
      float anim = this.animation.getValue() * this.visible.getValue();
      if (anim != 0.0F) {
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, Math.min(1.0F, anim));
         float scale = 0.5F + anim * 0.5F - 0.05F * this.selecting.getValue();
         RenderUtility.scale(context.getMatrices(), this.x + this.width / 2.0F, this.y + this.height / 2.0F, scale);
         this.renderComponent(context);
         RenderUtility.end(context.getMatrices());
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      }
   }

   protected abstract void renderComponent(UIContext var1);

   public void update(UIContext context) {
      float oldWidth = this.widthAnim.getValue();
      this.widthAnim.update(this.width);
      float newWidth = this.widthAnim.getValue();
      float widthDelta = newWidth - oldWidth;
      boolean isLeftSide = this.x + this.width / 2.0F < IScaledResolution.sr.getScaledWidth() / 2.0F;
      if (!isLeftSide) {
         this.x -= widthDelta;
      }

      if (widthDelta != 0.0F) {
         for (HudElement otherElement : Rockstar.getInstance().getHud().getElements()) {
            if (otherElement != this && otherElement.isShowing()) {
               float verticalOverlap = Math.min(this.y + this.height, otherElement.y + otherElement.height) - Math.max(this.y, otherElement.y);
               if (!(verticalOverlap <= 0.0F)) {
                  if (isLeftSide) {
                     float rightEdge = this.x + newWidth;
                     float distanceToOther = otherElement.x - rightEdge;
                     if (distanceToOther >= -5.0F && distanceToOther <= 25.0F) {
                        otherElement.x += widthDelta;
                        otherElement.x = Math.max(0.0F, Math.min(otherElement.x, IScaledResolution.sr.getScaledWidth() - otherElement.width));
                     }
                  } else {
                     float leftEdge = this.x;
                     float distanceToOther = leftEdge - (otherElement.x + otherElement.width);
                     if (distanceToOther >= -5.0F && distanceToOther <= 25.0F) {
                        otherElement.x -= widthDelta;
                        otherElement.x = Math.max(0.0F, Math.min(otherElement.x, IScaledResolution.sr.getScaledWidth() - otherElement.width));
                     }
                  }
               }
            }
         }
      }

      this.width = newWidth;
      this.dragAnim.update(this.dragging);
      this.animation.setEasing(this.showing ? Easing.BAKEK : Easing.BAKEK_BACK);
      this.animation.update(this.showing);
      this.visible.setEasing(this.show() ? Easing.BAKEK : Easing.BAKEK_BACK);
      this.visible.update(this.show());
      this.selecting.update(this.select);
      this.blurAnim.update(this.animation.getValue() >= 0.6F);
      if (this.dragging) {
         double mouseX = GuiUtility.getMouse().getX();
         double mouseY = GuiUtility.getMouse().getY();
         this.x = Math.max(0.0F, Math.min(IScaledResolution.sr.getScaledWidth() - this.width, (float)mouseX - this.dragX));
         this.y = Math.max(0.0F, Math.min(IScaledResolution.sr.getScaledHeight() - this.height, (float)mouseY - this.dragY));
         System.out.println("[DRAG_DEBUG] DRAGGING " + this.name + " -> x=" + this.x + ", y=" + this.y + " (mouse=" + mouseX + "," + mouseY + ")");
      }

      if (this.isHovered(context) && this.animation.getValue() >= 1.0F) {
         CursorUtility.set(CursorType.HAND);
      }
   }

   public void onMouseClicked(double mouseX, double mouseY, MouseButton button) {
      double realMouseX = GuiUtility.getMouse().getX();
      double realMouseY = GuiUtility.getMouse().getY();
      System.out
         .println(
            "[DRAG_DEBUG] Clicked! isHovered="
               + this.isHovered(realMouseX, realMouseY)
               + ", name="
               + this.name
               + ", realX="
               + realMouseX
               + ", realY="
               + realMouseY
               + ", thisX="
               + this.x
               + ", thisY="
               + this.y
         );
      if (this.isHovered(realMouseX, realMouseY) && this.showing) {
         if (button == MouseButton.LEFT) {
            this.dragging = true;
            this.dragX = (float)(realMouseX - this.x);
            this.dragY = (float)(realMouseY - this.y);
            this.startDragX = this.x;
            this.startDragY = this.y;
            System.out.println("[DRAG_DEBUG] DRAG STARTED! dragX=" + this.dragX + ", dragY=" + this.dragY);
         } else if (button == MouseButton.RIGHT) {
            this.select = true;
            this.loadingAnim.setValue(0.0F);
            Popup popup = new Popup((float)mouseX, (float)mouseY, 110.0F, 6.0F)
               .title(Localizator.translate(this.settings.isEmpty() ? "actions" : "settings"))
               .separator();

            for (Setting setting : this.settings) {
               popup.setting(setting);
            }

            popup.button(Localizator.translate("remove"), "icons/hud/trash.png", popup1 -> {
               this.showing = false;
               popup1.setShowing(false);
               Rockstar.getInstance().getFileManager().writeFile("client");
            }).onClose(() -> this.select = false);
            Rockstar.getInstance().getHud().getPopups().add(popup);
         }
      }
   }

   public void onMouseReleased(double mouseX, double mouseY, MouseButton button) {
      if (this.dragging && button == MouseButton.LEFT) {
         this.dragging = false;
         if (this.x != this.startDragX || this.y != this.startDragY) {
            Rockstar.getInstance().getHud().getHistoryManager().registerMove(this, this.startDragX, this.startDragY, this.x, this.y);
         }

         Rockstar.getInstance().getFileManager().writeFile("client");
      }
   }

   private float snapToLine(GridLine line, float pos, List<Float> offsets, List<Float> adjustments) {
      for (int i = 0; i < offsets.size(); i++) {
         float distance = Math.abs(pos + offsets.get(i) - line.getPos());
         if (distance < 25.0F) {
            line.setActive(true);
         }

         if (distance < 5.0F) {
            pos = line.getPos() + adjustments.get(i);
         }
      }

      return pos;
   }

   public boolean show() {
      return true;
   }

   public boolean isHovered(float mouseX, float mouseY) {
      return GuiUtility.isHovered(this.x, this.y, this.width, this.height, mouseX, mouseY);
   }

   public boolean isHovered(double mouseX, double mouseY) {
      return GuiUtility.isHovered(this.x, this.y, this.width, this.height, mouseX, mouseY);
   }

   public boolean isHovered(UIContext context) {
      return this.isHovered(context.getMouseX(), context.getMouseY());
   }

   public void pos(float x, float y) {
      this.x = x;
      this.y = y;
   }

   @Generated
   public float getX() {
      return this.x;
   }

   @Generated
   public float getY() {
      return this.y;
   }

   @Generated
   public float getWidth() {
      return this.width;
   }

   @Generated
   public float getHeight() {
      return this.height;
   }

   @Generated
   public Animation getAnimation() {
      return this.animation;
   }

   @Generated
   public Animation getVisible() {
      return this.visible;
   }

   @Generated
   public Animation getSelecting() {
      return this.selecting;
   }

   @Generated
   public Animation getDragAnim() {
      return this.dragAnim;
   }

   @Generated
   public Animation getBlurAnim() {
      return this.blurAnim;
   }

   @Generated
   public Animation getLoadingAnim() {
      return this.loadingAnim;
   }

   @Generated
   public Animation getWidthAnim() {
      return this.widthAnim;
   }

   @Generated
   public Animation getHeightAnim() {
      return this.heightAnim;
   }

   @Generated
   public boolean isShowing() {
      return this.showing;
   }

   @Generated
   public boolean isSelect() {
      return this.select;
   }

   @Generated
   @Override
   public List<Setting> getSettings() {
      return this.settings;
   }

   @Generated
   public boolean isDragging() {
      return this.dragging;
   }

   @Generated
   public float getDragX() {
      return this.dragX;
   }

   @Generated
   public float getDragY() {
      return this.dragY;
   }

   @Generated
   public float getStartDragX() {
      return this.startDragX;
   }

   @Generated
   public float getStartDragY() {
      return this.startDragY;
   }

   @Generated
   public String getName() {
      return this.name;
   }

   @Generated
   public String getIcon() {
      return this.icon;
   }

   @Generated
   public void setX(float x) {
      this.x = x;
   }

   @Generated
   public void setY(float y) {
      this.y = y;
   }

   @Generated
   public void setWidth(float width) {
      this.width = width;
   }

   @Generated
   public void setHeight(float height) {
      this.height = height;
   }

   @Generated
   public void setShowing(boolean showing) {
      this.showing = showing;
   }

   @Generated
   public void setSelect(boolean select) {
      this.select = select;
   }

   @Generated
   public void setSettings(List<Setting> settings) {
      this.settings = settings;
   }

   @Generated
   public void setDragging(boolean dragging) {
      this.dragging = dragging;
   }

   @Generated
   public void setDragX(float dragX) {
      this.dragX = dragX;
   }

   @Generated
   public void setDragY(float dragY) {
      this.dragY = dragY;
   }

   @Generated
   public void setStartDragX(float startDragX) {
      this.startDragX = startDragX;
   }

   @Generated
   public void setStartDragY(float startDragY) {
      this.startDragY = startDragY;
   }
}
