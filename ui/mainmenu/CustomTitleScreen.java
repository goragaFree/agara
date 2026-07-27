package moscow.rockstar.ui.mainmenu;

import java.util.ArrayList;
import java.util.List;
import moscow.rockstar.Rockstar;
import moscow.rockstar.framework.base.CustomScreen;
import moscow.rockstar.framework.base.UIContext;
import moscow.rockstar.framework.msdf.Font;
import moscow.rockstar.framework.msdf.Fonts;
import moscow.rockstar.framework.objects.BorderRadius;
import moscow.rockstar.framework.objects.MouseButton;
import moscow.rockstar.framework.objects.gradient.impl.VerticalGradient;
import moscow.rockstar.systems.localization.Localizator;
import moscow.rockstar.systems.modules.modules.other.Sounds;
import moscow.rockstar.utility.animation.base.Animation;
import moscow.rockstar.utility.animation.base.Easing;
import moscow.rockstar.utility.colors.ColorRGBA;
import moscow.rockstar.utility.game.TextUtility;
import moscow.rockstar.utility.interfaces.IMinecraft;
import moscow.rockstar.utility.math.MathUtility;
import moscow.rockstar.utility.render.DrawUtility;
import moscow.rockstar.utility.render.RenderUtility;
import moscow.rockstar.utility.render.obj.Rect;
import moscow.rockstar.utility.sounds.ClientSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;

public class CustomTitleScreen extends CustomScreen implements IMinecraft {
   private static boolean once;
   private static final List<CustomButton> buttons = new ArrayList<>();
   private boolean active;
   private final Animation activeAnimation = new Animation(800L, 0.0F, Easing.FIGMA_EASE_IN_OUT);
   private static final float COLON_RAISE = 4.5F;

   protected void init() {
      String basePath = "image/mainmenu/icons/";
      if (!once) {
         if (Rockstar.getInstance().getModuleManager().getModule(Sounds.class).isEnabled()) {
            ClientSounds.WELCOME.play(Rockstar.getInstance().getModuleManager().getModule(Sounds.class).getVolume().getCurrentValue());
         }

         buttons.add(new CustomButton(basePath + "single.png", 12.0F, () -> mc.setScreen(new SelectWorldScreen(this))));
         buttons.add(new CustomButton(basePath + "multi.png", 12.0F, () -> mc.setScreen(new MultiplayerScreen(this))));
         buttons.add(new CustomButton(basePath + "settings.png", 12.0F, () -> mc.setScreen(new OptionsScreen(this, mc.options))));
         buttons.add(new CustomButton(basePath + "quit.png", 14.0F, () -> mc.stop()));
         once = true;
      }

      super.init();
   }

   @Override
   public void render(UIContext context) {
      Font timeFont = Fonts.ROUND_BOLD.getFont(65.0F);
      Font dateFont = Fonts.MEDIUM.getFont(16.0F);
      Font unlockFont = Fonts.REGULAR.getFont(10.0F);
      float textAlpha = 255.0F * (0.5F + 0.5F * this.activeAnimation.getValue());
      float timeOffset = MathUtility.interpolate(this.height / 2.0F - 20.0F, 80.0, this.activeAnimation.getValue());
      Rect rect = new Rect(-this.width / 2.0F, -this.width / 3.0F, this.width * 1.5F, this.width);
      this.activeAnimation.update(this.active);
      context.drawRoundedRect(
         0.0F,
         0.0F,
         this.width,
         this.height,
         BorderRadius.ZERO,
         new VerticalGradient(new ColorRGBA(26.0F, 34.0F, 56.0F), new ColorRGBA(5.0F, 3.0F, 12.0F))
      );
      RenderUtility.scale(context.getMatrices(), this.width / 2.0F, this.height / 2.0F, 1.1F - 0.1F * this.activeAnimation.getValue());
      context.drawTexture(Rockstar.id("image/mainmenu/background.png"), rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
      RenderUtility.end(context.getMatrices());
      context.drawCenteredText(dateFont, TextUtility.getFormattedDate(), this.width / 2.0F, timeOffset - 23.0F, ColorRGBA.WHITE.withAlpha(textAlpha));
      this.drawTimeWithRaisedColon(context, timeFont, TextUtility.getCurrentTime(), this.width / 2.0F, timeOffset, ColorRGBA.WHITE.withAlpha(textAlpha));
      float nextAlpha = 155.0F * (1.0F - this.activeAnimation.getValue());
      if (nextAlpha > 1.0F) {
         context.drawCenteredText(
            unlockFont,
            Localizator.translate("mainmenu.next"),
            this.width / 2.0F,
            this.height - 15 + 3.0F * this.activeAnimation.getValue(),
            ColorRGBA.WHITE.withAlpha(nextAlpha)
         );
      }

      float userAlpha = 255.0F * this.activeAnimation.getValue();
      if (userAlpha > 1.0F && this.height > 424) {
         String userName = System.getProperty("user.name");
         float baseY = this.height - 25;
         float offset = 5.0F * (1.0F - this.activeAnimation.getValue());
         float userY = baseY - offset;
         context.drawCenteredText(Fonts.REGULAR.getFont(11.0F), userName, this.width / 2.0F, userY, ColorRGBA.WHITE.withAlpha(userAlpha));
      }

      DrawUtility.blurProgram.draw();
      float offset = 0.0F;
      float totalWidth = buttons.size() * 30.0F + (buttons.size() - 1) * 6.0F;
      float startX = this.width / 2.0F - totalWidth / 2.0F;
      float yPos = this.height > 460 ? this.height / 2.0F + 20.0F : this.height / 1.25F;

      for (CustomButton button : buttons) {
         button.getActiveAnim().update(buttons.size() - buttons.indexOf(button) > (1.0F - this.activeAnimation.getValue()) * buttons.size() + 0.5F);
         float xPos = startX + offset;
         button.set(xPos, yPos - 5.0F - 10.0F * button.getActiveAnim().getValue(), 30.0F, 30.0F);
         offset += button.getWidth() + 6.0F;
         button.draw(context);
      }

      if (this.shouldShowIsland()) {
         Rockstar.getInstance().getHud().getIsland().render(context);
      }
   }

   private void drawTimeWithRaisedColon(UIContext context, Font font, String time, float centerX, float y, ColorRGBA color) {
      int colonIdx = time.indexOf(58);
      if (colonIdx < 0) {
         context.drawCenteredText(font, time, centerX, y, color);
      } else {
         String left = time.substring(0, colonIdx);
         String colon = ":";
         String right = time.substring(colonIdx + 1);
         float leftW = font.getFont().getWidth(left, font.getSize());
         float colonW = font.getFont().getWidth(colon, font.getSize());
         float rightW = font.getFont().getWidth(right, font.getSize());
         float totalW = leftW + colonW + rightW;
         float startX = centerX - totalW / 2.0F;
         context.drawText(font, left, startX, y, color);
         context.drawText(font, colon, startX + leftW, y - 4.5F, color);
         context.drawText(font, right, startX + leftW + colonW, y, color);
      }
   }

   @Override
   public void onMouseClicked(double mouseX, double mouseY, MouseButton button) {
      if (!this.shouldShowIsland() || !Rockstar.getInstance().getHud().getIsland().handleClick((float)mouseX, (float)mouseY, button.getButtonIndex())) {
         for (CustomButton customButton : buttons) {
            if (customButton.hovered(mouseX, mouseY) && customButton.getActiveAnim().getValue() == 1.0F) {
               customButton.click(mouseX, mouseY, button.getButtonIndex());
               return;
            }
         }

         float animProgress = this.activeAnimation.getValue();
         if (!(animProgress > 0.0F) || !(animProgress < 1.0F)) {
            this.active = !this.active;
            super.onMouseClicked(mouseX, mouseY, button);
         }
      }
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == 69) {
         Rockstar.getInstance().getThemeManager().switchTheme();
      }

      if (Screen.hasControlDown() && keyCode == 82) {
         MinecraftClient.getInstance().setScreen(new MultiplayerScreen(this));
      }

      if (Screen.hasControlDown() && keyCode == 84) {
         MinecraftClient.getInstance().setScreen(new SelectWorldScreen(this));
      }

      return super.keyPressed(keyCode, scanCode, modifiers);
   }

   private boolean shouldShowIsland() {
      return Rockstar.getInstance().getMusicTracker().haveActiveSession();
   }

   public boolean shouldCloseOnEsc() {
      return false;
   }
}
