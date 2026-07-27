package moscow.rockstar.ui.menu.dropdown.components.settings.impl;

import java.util.HashMap;
import java.util.Map;
import moscow.rockstar.Rockstar;
import moscow.rockstar.framework.base.CustomComponent;
import moscow.rockstar.framework.base.UIContext;
import moscow.rockstar.framework.msdf.Font;
import moscow.rockstar.framework.msdf.Fonts;
import moscow.rockstar.framework.objects.BorderRadius;
import moscow.rockstar.framework.objects.MouseButton;
import moscow.rockstar.systems.localization.Localizator;
import moscow.rockstar.systems.setting.settings.ModeSetting;
import moscow.rockstar.ui.menu.dropdown.components.settings.MenuSettingComponent;
import moscow.rockstar.utility.colors.Colors;
import moscow.rockstar.utility.game.cursor.CursorType;
import moscow.rockstar.utility.game.cursor.CursorUtility;
import moscow.rockstar.utility.gui.GuiUtility;
import moscow.rockstar.utility.render.DrawUtility;
import moscow.rockstar.utility.render.ScissorUtility;
import moscow.rockstar.utility.render.penis.PenisPlayer;

public class ModeSettingComponent extends MenuSettingComponent<ModeSetting> {
   private boolean initialized;
   private final Map<ModeSetting.Value, ModeSettingComponent.ValueScroll> scrollMap = new HashMap<>();

   public ModeSettingComponent(ModeSetting setting, CustomComponent parent) {
      super(setting, parent);
   }

   @Override
   protected void renderComponent(UIContext context) {
      if (!this.initialized) {
         for (ModeSetting.Value value : this.setting.getValues()) {
            value.setEnablePenis(new PenisPlayer(Rockstar.id("penises/check_enable.penis")));
            value.setDisablePenis(new PenisPlayer(Rockstar.id("penises/check_disable.penis")));
            value.setLastState(value.isSelected());
            value.setCurrentPenis(value.isLastState() ? value.getEnablePenis() : value.getDisablePenis());
            if (value.isLastState()) {
               value.getEnablePenis().playOnce();
            } else {
               value.getDisablePenis().setFrame(0);
               value.getDisablePenis().stop();
            }
         }

         this.initialized = true;
      }

      float xPos = this.x + 9.0F;
      float yPos = this.y + 1.0F;
      float menuWidth = this.width - 18.0F;
      Font mainFont = Fonts.REGULAR.getFont(8.0F);
      float headerHeight = 19.0F;
      this.hoverAnimation.update(this.isHovered(context.getMouseX(), context.getMouseY()));
      this.drawSettingName(
         context,
         mainFont,
         Localizator.translate(this.setting.getName()),
         this.x + 10.0F,
         yPos - 1.0F + GuiUtility.getMiddleOfBox(mainFont.height(), headerHeight),
         Colors.getTextColor().withAlpha(255.0F * (0.75F + 0.25F * this.hoverAnimation.getValue())),
         this.getParent().getWidth() - 10.0F
      );
      float listHeight = 8 + this.setting.getValues().size() * 12;
      context.drawRoundedRect(xPos - 1.0F, yPos + 17.0F, menuWidth + 2.0F, listHeight, BorderRadius.all(6.0F), Colors.getBackgroundColor().withAlpha(76.5F));
      float verticalOffset = 0.0F;
      Font valueFont = Fonts.REGULAR.getFont(7.0F);

      for (ModeSetting.Value value : this.setting.getValues()) {
         if (!value.isHidden()) {
            boolean selected = value.isSelected();
            if (selected != value.isLastState()) {
               value.setCurrentPenis(selected ? value.getEnablePenis() : value.getDisablePenis());
               value.getCurrentPenis().playOnce();
               value.setLastState(selected);
            }

            value.getCurrentPenis().update();
            float itemY = yPos + 20.0F + verticalOffset;
            float itemHeight = 12.0F;
            boolean isRowHovered = GuiUtility.isHovered(xPos - 1.0F, itemY, menuWidth + 2.0F, itemHeight, context.getMouseX(), context.getMouseY());
            if (isRowHovered) {
               CursorUtility.set(CursorType.HAND);
            }

            value.getHoverAnimation().update(isRowHovered);
            value.getActiveAnimation().update(selected);
            String valName = Localizator.translate(value.getName());
            float valWidth = valueFont.width(valName);
            float maxValWidth = menuWidth - 22.0F;
            ModeSettingComponent.ValueScroll scroll = this.scrollMap.computeIfAbsent(value, v -> new ModeSettingComponent.ValueScroll());
            scroll.tick(valWidth, maxValWidth, isRowHovered);
            float drawX = xPos + 7.0F;
            float drawY = yPos + 24.5F + verticalOffset;
            ScissorUtility.push(context.getMatrices(), drawX, drawY - 2.0F, maxValWidth, valueFont.height() + 4.0F);
            context.drawFadeoutText(
               valueFont,
               valName,
               drawX - scroll.offset,
               drawY,
               Colors.getTextColor().withAlpha(255.0F * (0.75F + 0.25F * value.getHoverAnimation().getValue() + 0.25F * value.getActiveAnimation().getValue())),
               0.96F,
               1.0F,
               maxValWidth + scroll.offset
            );
            ScissorUtility.pop();
            if (value.getActiveAnimation().getValue() > 0.0F || value.getCurrentPenis().isPlaying()) {
               DrawUtility.drawAnimationSprite(
                  context.getMatrices(),
                  value.getCurrentPenis().getCurrentSprite(),
                  xPos + menuWidth - 11.0F - value.getActiveAnimation().getValue() * 2.0F,
                  yPos + 24.0F + verticalOffset,
                  6.0F,
                  6.0F,
                  Colors.getTextColor().mulAlpha(0.1F + 0.9F * value.getActiveAnimation().getValue())
               );
            }

            verticalOffset += 12.0F;
         }
      }
   }

   @Override
   public void drawSplit(UIContext context) {
      context.drawRect(this.x, this.y + this.height, this.width, 0.5F, Colors.getTextColor().withAlpha(5.1F));
   }

   @Override
   public void onMouseClicked(double mouseX, double mouseY, MouseButton button) {
      if (button == MouseButton.LEFT) {
         float verticalOffset = 0.0F;

         for (ModeSetting.Value value : this.setting.getValues()) {
            if (!value.isHidden()) {
               if (GuiUtility.isHovered(this.x + 8.0F, this.y + 20.0F + verticalOffset, this.width - 16.0F, 12.0, mouseX, mouseY)) {
                  value.select();
               }

               verticalOffset += 12.0F;
            }
         }

         super.onMouseClicked(mouseX, mouseY, button);
      }
   }

   @Override
   public float getHeight() {
      this.height = 31 + this.setting.getValues().size() * 12;
      return this.height;
   }

   private static class ValueScroll {
      float offset = 0.0F;
      long lastMs = System.currentTimeMillis();
      long pauseUntil = 0L;
      boolean forward = true;

      void tick(float textW, float maxW, boolean hovered) {
         long now = System.currentTimeMillis();
         float delta = (float)(now - this.lastMs) / 1000.0F;
         this.lastMs = now;
         if (textW <= maxW) {
            this.offset = 0.0F;
         } else {
            if (hovered) {
               if (now > this.pauseUntil) {
                  float speed = 35.0F;
                  float limit = textW - maxW;
                  if (this.forward) {
                     this.offset += delta * speed;
                     if (this.offset >= limit) {
                        this.offset = limit;
                        this.forward = false;
                        this.pauseUntil = now + 600L;
                     }
                  } else {
                     this.offset -= delta * speed;
                     if (this.offset <= 0.0F) {
                        this.offset = 0.0F;
                        this.forward = true;
                        this.pauseUntil = now + 1000L;
                     }
                  }
               }
            } else if (this.offset > 0.0F) {
               this.offset = Math.max(0.0F, this.offset - delta * 60.0F);
               this.forward = true;
               this.pauseUntil = now + 400L;
            }
         }
      }
   }
}
