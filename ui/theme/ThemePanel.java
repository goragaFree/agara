package moscow.rockstar.ui.theme;

import moscow.rockstar.Rockstar;
import moscow.rockstar.framework.base.CustomComponent;
import moscow.rockstar.framework.base.UIContext;
import moscow.rockstar.framework.msdf.Fonts;
import moscow.rockstar.framework.objects.BorderRadius;
import moscow.rockstar.framework.objects.MouseButton;
import moscow.rockstar.systems.modules.modules.visuals.Interface;
import moscow.rockstar.systems.theme.Theme;
import moscow.rockstar.ui.components.ColorPicker;
import moscow.rockstar.ui.components.textfield.TextField;
import moscow.rockstar.utility.animation.base.Animation;
import moscow.rockstar.utility.animation.base.Easing;
import moscow.rockstar.utility.colors.ColorRGBA;
import moscow.rockstar.utility.colors.Colors;
import moscow.rockstar.utility.game.cursor.CursorType;
import moscow.rockstar.utility.game.cursor.CursorUtility;
import moscow.rockstar.utility.gui.GuiUtility;
import moscow.rockstar.utility.interfaces.IScaledResolution;

public class ThemePanel extends CustomComponent implements IScaledResolution {
   private static final float COLLAPSED_WIDTH = 42.0F;
   private static final float EXPANDED_WIDTH = 158.0F;
   private static final float COLLAPSED_HEIGHT = 34.0F;
   private static final float EXPANDED_HEIGHT = 206.0F;
   private static final String TXT_THEMES = "Темы";
   private static final String TXT_PLACEHOLDER = "Введите название";
   private static final String TXT_COLORS = "Цвета";
   private static final String TXT_ACCENT = "Акцент";
   private static final String TXT_BACKGROUND = "Фон";
   private static final String TXT_ADDITIONAL = "Дополнительный";
   private static final String TXT_TEXT = "Текст";
   private static final String TXT_OUTLINE = "Обводка";
   private static final String TXT_OTHER = "Другое";
   private final Animation expandAnimation = new Animation(250L, Easing.FIGMA_EASE_IN_OUT);
   private boolean expanded;
   private float menuAlpha = 1.0F;
   private ColorPicker colorPicker;
   private ThemePanel.EditedColor editedColor;
   private final TextField nameField = new TextField(Fonts.REGULAR.getFont(6.5F));
   private final Animation addButtonHover = new Animation(200L, 0.0F, Easing.FIGMA_EASE_IN_OUT);

   public void setMenuAlpha(float menuAlpha) {
      this.menuAlpha = Math.max(0.0F, Math.min(1.0F, menuAlpha));
   }

   @Override
   public void onInit() {
      this.width = 42.0F;
      this.height = 34.0F;
      this.nameField.setPreview("Введите название");
   }

   @Override
   protected void renderComponent(UIContext context) {
      this.expandAnimation.update(this.expanded);
      float expand = this.expandAnimation.getValue();
      this.width = 42.0F + 116.0F * expand;
      this.height = 34.0F + 172.0F * expand;
      this.x = sr.getScaledWidth() - this.width - 18.0F;
      this.y = sr.getScaledHeight() - this.height - 26.0F;
      float alpha = this.menuAlpha;
      float radius = 10.0F;
      if (Interface.showGlass()) {
         context.drawLiquidGlass(
            this.x, this.y, this.width, this.height, radius, Interface.glassDistortion(), BorderRadius.all(radius), ColorRGBA.WHITE.withAlpha(255.0F * alpha)
         );
      }

      if (Interface.showMinimalizm()) {
         context.drawBlurredRect(this.x, this.y, this.width, this.height, 45.0F, BorderRadius.all(radius), ColorRGBA.WHITE.withAlpha(255.0F * alpha));
      }

      float fillAlpha = Interface.glassSelected()
         ? Interface.glassOpacity() / 100.0F
         : Rockstar.getInstance().getThemeManager().getCurrentTheme().getHudAlpha();
      context.drawRoundedRect(
         this.x, this.y, this.width, this.height, BorderRadius.all(radius), new ColorRGBA(18.0F, 18.0F, 20.0F).withAlpha(255.0F * fillAlpha * alpha)
      );
      context.drawRoundedRect(this.x + 10.0F, this.y + 11.5F, 11.0F, 11.0F, BorderRadius.all(5.5F), Colors.getAccent().withAlpha(255.0F * alpha));
      if (expand <= 0.05F) {
         if (this.isHovered(context.getMouseX(), context.getMouseY())) {
            CursorUtility.set(CursorType.HAND);
         }

         this.renderColorPicker(context);
      } else {
         float ca = alpha * expand;
         float headerY = this.y + 11.0F;
         context.drawText(Fonts.MEDIUM.getFont(8.5F), "Темы", this.x + 24.0F, headerY + 1.0F, Colors.getTextColor().withAlpha(240.0F * ca));
         context.drawRect(this.x + 10.0F, headerY + 15.0F, this.width - 20.0F, 0.5F, Colors.getAdditionalColor().withAlpha(150.0F * ca));
         float dividerY = headerY + 15.0F;
         float inputY = dividerY + 6.0F;
         float inputH = 16.0F;
         float addBtnW = 16.0F;
         float inputW = this.width - 16.0F - addBtnW - 4.0F;
         context.drawRoundedRect(this.x + 8.0F, inputY, inputW, inputH, BorderRadius.all(5.0F), Colors.getAdditionalColor().withAlpha(55.0F * ca));
         this.nameField.set(this.x + 10.0F, inputY + 1.0F, inputW - 4.0F, inputH - 2.0F);
         this.nameField.setAlpha(ca);
         this.nameField.setTextColor(Colors.getTextColor());
         this.nameField.render(context);
         float addBtnX = this.x + 8.0F + inputW + 4.0F;
         boolean addHovered = GuiUtility.isHovered(addBtnX, inputY, addBtnW, inputH, context.getMouseX(), context.getMouseY());
         this.addButtonHover.update(addHovered);
         ColorRGBA addBtnColor = new ColorRGBA(65.0F, 155.0F, 245.0F).mix(new ColorRGBA(90.0F, 175.0F, 255.0F), this.addButtonHover.getValue());
         context.drawRoundedRect(addBtnX, inputY, addBtnW, inputH, BorderRadius.all(5.0F), addBtnColor.withAlpha(230.0F * ca));
         context.drawCenteredText(Fonts.MEDIUM.getFont(8.0F), "+", addBtnX + addBtnW / 2.0F, inputY + 4.0F, ColorRGBA.WHITE.withAlpha(240.0F * ca));
         if (addHovered) {
            CursorUtility.set(CursorType.HAND);
         }

         float dotsRowY = inputY + inputH + 6.0F;
         float dotSize = 7.0F;
         float dotX = this.x + 11.0F;

         for (Theme theme : Theme.values()) {
            boolean isCurrent = Rockstar.getInstance().getThemeManager().getCurrentTheme() == theme;
            ColorRGBA dotColor = theme.getAccentColor();
            context.drawRoundedRect(dotX, dotsRowY, dotSize, dotSize, BorderRadius.all(dotSize / 2.0F), dotColor.withAlpha(255.0F * ca));
            if (isCurrent) {
               context.drawRoundedRect(
                  dotX - 1.5F, dotsRowY - 1.5F, dotSize + 3.0F, dotSize + 3.0F, BorderRadius.all((dotSize + 3.0F) / 2.0F), dotColor.withAlpha(80.0F * ca)
               );
            }

            dotX += dotSize + 5.0F;
         }

         float colorsLabelY = dotsRowY + dotSize + 7.0F;
         context.drawText(Fonts.MEDIUM.getFont(7.5F), "Цвета", this.x + 10.0F, colorsLabelY, Colors.getTextColor().withAlpha(230.0F * ca));
         float rowStartY = colorsLabelY + 13.0F;
         float rowStep = 14.0F;
         this.drawColorRow(context, "Акцент", Colors.getAccent(), rowStartY, ca, ThemePanel.EditedColor.ACCENT);
         this.drawColorRow(context, "Фон", Colors.getBackgroundColor(), rowStartY + rowStep, ca, ThemePanel.EditedColor.BACKGROUND);
         this.drawColorRow(context, "Дополнительный", Colors.getAdditionalColor(), rowStartY + rowStep * 2.0F, ca, ThemePanel.EditedColor.ADDITIONAL);
         this.drawColorRow(context, "Текст", Colors.getTextColor(), rowStartY + rowStep * 3.0F, ca, ThemePanel.EditedColor.TEXT);
         this.drawColorRow(context, "Обводка", Colors.getOutlineColor(), rowStartY + rowStep * 4.0F, ca, ThemePanel.EditedColor.OUTLINE);
         float otherLabelY = rowStartY + rowStep * 5.0F + 4.0F;
         context.drawText(Fonts.MEDIUM.getFont(7.5F), "Другое", this.x + 10.0F, otherLabelY, Colors.getTextColor().withAlpha(230.0F * ca));
         if (this.isHovered(context.getMouseX(), context.getMouseY())) {
            CursorUtility.set(CursorType.HAND);
         }

         this.renderColorPicker(context);
      }
   }

   private void drawColorRow(UIContext context, String label, ColorRGBA color, float y, float alpha, ThemePanel.EditedColor target) {
      boolean hovered = this.isColorRowHovered(y, context.getMouseX(), context.getMouseY());
      context.drawRoundedRect(
         this.x + 7.0F, y - 3.5F, this.width - 14.0F, 12.5F, BorderRadius.all(4.0F), Colors.getAdditionalColor().withAlpha((hovered ? 70.0F : 0.0F) * alpha)
      );
      context.drawText(Fonts.REGULAR.getFont(6.5F), label, this.x + 11.0F, y, Colors.getTextColor().withAlpha(190.0F * alpha));
      float dotRightX = this.x + this.width - 18.0F;
      context.drawRoundedRect(dotRightX - 0.8F, y - 1.8F, 9.6F, 9.6F, BorderRadius.all(4.8F), Colors.getOutlineColor().withAlpha(180.0F * alpha));
      context.drawRoundedRect(dotRightX, y, 8.0F, 8.0F, BorderRadius.all(4.0F), color.withAlpha(255.0F * alpha));
      if (hovered) {
         CursorUtility.set(CursorType.HAND);
      }
   }

   private boolean isColorRowHovered(float rowY, double mouseX, double mouseY) {
      return GuiUtility.isHovered(this.x + 7.0F, rowY - 3.5F, this.width - 14.0F, 12.5, mouseX, mouseY);
   }

   private void renderColorPicker(UIContext context) {
      if (this.colorPicker != null) {
         this.colorPicker.render(context);
         if (this.editedColor != null) {
            this.applyEditedColor(this.colorPicker.built());
         }

         if (!this.colorPicker.isShowing() && this.colorPicker.getAnimation().getValue() == 0.0F) {
            this.colorPicker = null;
            this.editedColor = null;
            Rockstar.getInstance().getThemeManager().flushSave();
         }
      }
   }

   private void openColorPicker(double mouseX, double mouseY, ThemePanel.EditedColor target, ColorRGBA color, String title) {
      this.editedColor = target;
      this.colorPicker = new ColorPicker((float)mouseX, (float)mouseY, 6.0F, false, color, title);
   }

   private void applyEditedColor(ColorRGBA color) {
      Theme theme = Rockstar.getInstance().getThemeManager().getCurrentTheme();
      switch (this.editedColor) {
         case ACCENT:
            theme.setAccentColor(color);
            break;
         case BACKGROUND:
            theme.setBackgroundColor(color);
            break;
         case ADDITIONAL:
            theme.setAdditionalColor(color);
            break;
         case TEXT:
            theme.setTextColor(color);
            break;
         case OUTLINE:
            theme.setOutlineColor(color);
      }
   }

   @Override
   public void onMouseClicked(double mouseX, double mouseY, MouseButton button) {
      this.nameField.onMouseClicked(mouseX, mouseY, button);
      if (this.colorPicker != null) {
         boolean pickerHovered = this.colorPicker.isHovered(mouseX, mouseY);
         boolean pickerWasPick = this.colorPicker.isPick();
         this.colorPicker.onMouseClicked(mouseX, mouseY, button);
         if (pickerHovered || pickerWasPick) {
            return;
         }

         if (!super.isHovered(mouseX, mouseY)) {
            this.colorPicker.setShowing(false);
         }
      }

      if (button == MouseButton.LEFT && this.isHovered(mouseX, mouseY)) {
         if (!this.expanded) {
            this.expanded = true;
         } else {
            float expand = this.expandAnimation.getValue();
            float dividerY = this.y + 34.0F - 1.0F;
            float inputY = dividerY + 6.0F;
            float inputH = 16.0F;
            float addBtnW = 16.0F;
            float inputW = this.width - 16.0F - addBtnW - 4.0F;
            float addBtnX = this.x + 8.0F + inputW + 4.0F;
            if (GuiUtility.isHovered(addBtnX, inputY, addBtnW, inputH, mouseX, mouseY)) {
               String name = this.nameField.getBuiltText().trim();
               this.nameField.clear();
               Rockstar.getInstance().getThemeManager().flushSave();
            } else {
               float dotsRowY = inputY + inputH + 6.0F;
               float dotSize = 7.0F;
               float dotX = this.x + 11.0F;

               for (Theme theme : Theme.values()) {
                  if (GuiUtility.isHovered(dotX, dotsRowY, dotSize, dotSize, mouseX, mouseY)) {
                     Rockstar.getInstance().getThemeManager().setCurrentTheme(theme);
                     Rockstar.getInstance().getThemeManager().flushSave();
                     return;
                  }

                  dotX += dotSize + 5.0F;
               }

               float colorsLabelY = dotsRowY + dotSize + 7.0F;
               float rowStartY = colorsLabelY + 13.0F;
               float rowStep = 14.0F;
               if (this.isColorRowHovered(rowStartY, mouseX, mouseY)) {
                  this.openColorPicker(mouseX, mouseY, ThemePanel.EditedColor.ACCENT, Colors.getAccent(), "Акцент");
               } else if (this.isColorRowHovered(rowStartY + rowStep, mouseX, mouseY)) {
                  this.openColorPicker(mouseX, mouseY, ThemePanel.EditedColor.BACKGROUND, Colors.getBackgroundColor(), "Фон");
               } else if (this.isColorRowHovered(rowStartY + rowStep * 2.0F, mouseX, mouseY)) {
                  this.openColorPicker(mouseX, mouseY, ThemePanel.EditedColor.ADDITIONAL, Colors.getAdditionalColor(), "Дополнительный");
               } else if (this.isColorRowHovered(rowStartY + rowStep * 3.0F, mouseX, mouseY)) {
                  this.openColorPicker(mouseX, mouseY, ThemePanel.EditedColor.TEXT, Colors.getTextColor(), "Текст");
               } else if (this.isColorRowHovered(rowStartY + rowStep * 4.0F, mouseX, mouseY)) {
                  this.openColorPicker(mouseX, mouseY, ThemePanel.EditedColor.OUTLINE, Colors.getOutlineColor(), "Обводка");
               } else if (GuiUtility.isHovered(this.x, this.y, this.width, 34.0, mouseX, mouseY)) {
                  this.expanded = false;
               }
            }
         }
      }
   }

   @Override
   public void onMouseReleased(double mouseX, double mouseY, MouseButton button) {
      this.nameField.onMouseReleased(mouseX, mouseY, button);
      if (this.colorPicker != null) {
         this.colorPicker.onMouseReleased(mouseX, mouseY, button);
         Rockstar.getInstance().getThemeManager().flushSave();
      }
   }

   @Override
   public void onKeyPressed(int keyCode, int scanCode, int modifiers) {
      this.nameField.onKeyPressed(keyCode, scanCode, modifiers);
      if (this.colorPicker != null) {
         this.colorPicker.onKeyPressed(keyCode, scanCode, modifiers);
      }
   }

   @Override
   public void onScroll(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
   }

   @Override
   public boolean isHovered(double mouseX, double mouseY) {
      return super.isHovered(mouseX, mouseY) || this.colorPicker != null && (this.colorPicker.isHovered(mouseX, mouseY) || this.colorPicker.isPick());
   }

   @Override
   public boolean isHovered(float mouseX, float mouseY) {
      return this.isHovered((double)mouseX, (double)mouseY);
   }

   public boolean isExpanded() {
      return this.expanded || this.expandAnimation.getValue() > 0.05F || this.colorPicker != null;
   }

   private enum EditedColor {
      ACCENT,
      BACKGROUND,
      ADDITIONAL,
      TEXT,
      OUTLINE;
   }
}
