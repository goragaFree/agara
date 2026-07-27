package moscow.rockstar.ui.menu.dropdown.components.settings;

import java.util.HashMap;
import java.util.Map;
import lombok.Generated;
import moscow.rockstar.Rockstar;
import moscow.rockstar.framework.base.CustomComponent;
import moscow.rockstar.framework.base.UIContext;
import moscow.rockstar.framework.msdf.Font;
import moscow.rockstar.systems.localization.Localizator;
import moscow.rockstar.systems.setting.Setting;
import moscow.rockstar.ui.components.popup.Popup;
import moscow.rockstar.ui.menu.dropdown.DropDownScreen;
import moscow.rockstar.ui.menu.dropdown.components.module.ModuleComponent;
import moscow.rockstar.utility.animation.base.Animation;
import moscow.rockstar.utility.animation.base.Easing;
import moscow.rockstar.utility.colors.ColorRGBA;
import moscow.rockstar.utility.render.ScissorUtility;

public abstract class MenuSettingComponent<T extends Setting> extends CustomComponent {
   private final CustomComponent parent;
   protected final T setting;
   private final Animation visibilityAnimation = new Animation(300L, Easing.BAKEK_PAGES);
   protected final Animation hoverAnimation = new Animation(300L, Easing.FIGMA_EASE_IN_OUT);
   private final Map<String, MenuSettingComponent.ScrollState> scrollStates = new HashMap<>();
   private static final float BASE_SCROLL_SPEED = 15.0F;
   private static final float MAX_SCROLL_SPEED = 60.0F;
   private static final long SCROLL_PAUSE = 800L;
   private static final long REVERSE_PAUSE = 450L;

   public MenuSettingComponent(T setting, CustomComponent parent) {
      this.parent = parent;
      this.setting = setting;
   }

   @Override
   public void update(UIContext context) {
      String translatedDescription = Localizator.translateOrEmpty(this.setting.getDescription());
      CustomComponent customComponent = this.parent;
      ModuleComponent component;
      if (customComponent instanceof ModuleComponent
         && (
            (component = (ModuleComponent)customComponent).getParent().isHovered(context) && this.isHovered(context)
               || Rockstar.getInstance().getMenuScreen() instanceof DropDownScreen
         )
         && Rockstar.getInstance().getMenuScreen() instanceof DropDownScreen screen) {
         screen.setDesc(Localizator.translate(translatedDescription));
      }

      if (this.parent instanceof Popup && this.isHovered(context)) {
         Rockstar.getInstance().getHud().setDesc(Localizator.translate(translatedDescription));
      }

      super.update(context);
   }

   @Override
   public void onInit() {
      super.onInit();
   }

   public float getOpacity() {
      return this.visibilityAnimation.getValue();
   }

   public void drawRegular8(UIContext context) {
   }

   public void drawSplit(UIContext context) {
   }

   protected void drawSettingName(UIContext context, Font font, String text, float x, float y, ColorRGBA color, float maxWidth) {
      this.drawSettingName(context, font, text, x, y, color, maxWidth, this.isHovered(context.getMouseX(), context.getMouseY()));
   }

   protected void drawSettingName(UIContext context, Font font, String text, float x, float y, ColorRGBA color, float maxWidth, boolean hoveredOverride) {
      float textWidth = font.width(text);
      if (textWidth <= maxWidth) {
         context.drawText(font, text, x, y, color);
      } else {
         MenuSettingComponent.ScrollState s = this.scrollStates.computeIfAbsent(text, k -> new MenuSettingComponent.ScrollState());
         long now = System.currentTimeMillis();
         float deltaTime = (float)(now - s.lastScrollTime);
         s.lastScrollTime = now;
         float maxScroll = textWidth - maxWidth;
         float overflowRatio = maxScroll / maxWidth;
         float speedFactor = Math.min(overflowRatio * 0.85F, 1.0F);
         float currentSpeed = 15.0F + 45.0F * speedFactor;
         float scrollSpeed = currentSpeed / 1000.0F;
         if (hoveredOverride) {
            if (now >= s.scrollPauseUntil) {
               float delta = deltaTime * scrollSpeed;
               if (s.scrollForward) {
                  s.offset += delta;
                  if (s.offset >= maxScroll) {
                     s.offset = maxScroll;
                     s.scrollForward = false;
                     s.scrollPauseUntil = now + 450L;
                  }
               } else {
                  s.offset -= delta;
                  if (s.offset <= 0.0F) {
                     s.offset = 0.0F;
                     s.scrollForward = true;
                     s.scrollPauseUntil = now + 450L;
                  }
               }
            }
         } else if (s.offset > 0.0F) {
            s.offset = Math.max(0.0F, s.offset - deltaTime * (scrollSpeed * 1.7F));
            s.scrollForward = true;
            s.scrollPauseUntil = now + 800L;
         }

         ScissorUtility.push(context.getMatrices(), x, y - 2.0F, maxWidth, font.height() + 4.0F);
         context.drawFadeoutText(font, text, x - s.offset, y, color, 0.96F, 1.0F, maxWidth + s.offset);
         ScissorUtility.pop();
      }
   }

   @Generated
   public CustomComponent getParent() {
      return this.parent;
   }

   @Generated
   public T getSetting() {
      return this.setting;
   }

   @Generated
   public Animation getVisibilityAnimation() {
      return this.visibilityAnimation;
   }

   @Generated
   public Animation getHoverAnimation() {
      return this.hoverAnimation;
   }

   private static class ScrollState {
      float offset = 0.0F;
      boolean scrollForward = true;
      long scrollPauseUntil = System.currentTimeMillis() + 800L;
      long lastScrollTime = System.currentTimeMillis();
   }
}
