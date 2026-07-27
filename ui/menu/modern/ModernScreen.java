package moscow.rockstar.ui.menu.modern;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import lombok.Generated;
import moscow.rockstar.Rockstar;
import moscow.rockstar.framework.base.UIContext;
import moscow.rockstar.framework.msdf.Fonts;
import moscow.rockstar.framework.objects.BorderRadius;
import moscow.rockstar.framework.objects.MouseButton;
import moscow.rockstar.systems.modules.Module;
import moscow.rockstar.systems.modules.modules.other.Sounds;
import moscow.rockstar.systems.modules.modules.visuals.Interface;
import moscow.rockstar.systems.modules.modules.visuals.MenuModule;
import moscow.rockstar.ui.components.ColorPicker;
import moscow.rockstar.ui.components.textfield.FieldAction;
import moscow.rockstar.ui.components.textfield.TextField;
import moscow.rockstar.ui.menu.MenuScreen;
import moscow.rockstar.ui.menu.api.MenuCategory;
import moscow.rockstar.ui.menu.dropdown.components.MenuPanel;
import moscow.rockstar.ui.menu.dropdown.components.settings.impl.BezierSettingComponent;
import moscow.rockstar.ui.menu.dropdown.components.settings.impl.BindSettingComponent;
import moscow.rockstar.ui.menu.dropdown.components.settings.impl.BooleanSettingComponent;
import moscow.rockstar.ui.menu.dropdown.components.settings.impl.ButtonSettingComponent;
import moscow.rockstar.ui.menu.dropdown.components.settings.impl.ColorSettingComponent;
import moscow.rockstar.ui.menu.dropdown.components.settings.impl.ModeSettingComponent;
import moscow.rockstar.ui.menu.dropdown.components.settings.impl.RangeSettingComponent;
import moscow.rockstar.ui.menu.dropdown.components.settings.impl.SliderSettingComponent;
import moscow.rockstar.ui.menu.dropdown.components.settings.impl.StringSettingComponent;
import moscow.rockstar.ui.menu.modern.components.ModernModule;
import moscow.rockstar.ui.menu.modern.components.ModernSettings;
import moscow.rockstar.ui.theme.ThemePanel;
import moscow.rockstar.utility.animation.base.Animation;
import moscow.rockstar.utility.animation.base.Easing;
import moscow.rockstar.utility.colors.Colors;
import moscow.rockstar.utility.game.cursor.CursorType;
import moscow.rockstar.utility.game.cursor.CursorUtility;
import moscow.rockstar.utility.gui.GuiUtility;
import moscow.rockstar.utility.gui.ScrollHandler;
import moscow.rockstar.utility.interfaces.IMinecraft;
import moscow.rockstar.utility.interfaces.IScaledResolution;
import moscow.rockstar.utility.render.DrawUtility;
import moscow.rockstar.utility.render.RenderUtility;
import moscow.rockstar.utility.render.ScissorUtility;
import moscow.rockstar.utility.render.batching.impl.FadeOutBatching;
import moscow.rockstar.utility.render.batching.impl.FontBatching;
import moscow.rockstar.utility.render.batching.impl.IconBatching;
import moscow.rockstar.utility.render.batching.impl.RoundedRectBatching;
import moscow.rockstar.utility.render.batching.impl.SquircleBatching;
import moscow.rockstar.utility.render.obj.Rect;
import moscow.rockstar.utility.render.penis.PenisPlayer;
import moscow.rockstar.utility.sounds.ClientSounds;
import moscow.rockstar.utility.time.Timer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.InputUtil;

public class ModernScreen extends MenuScreen implements IMinecraft, IScaledResolution {
   private final Rect menuWindow;
   private float dragX;
   private float dragY;
   private boolean drag;
   private final ScrollHandler scrollHandler = new ScrollHandler();
   private MenuCategory current = MenuCategory.COMBAT;
   private final List<ColorPicker> colorPickers = new LinkedList<>();
   private final List<ModernCategory> categories = new ArrayList<>();
   private final List<ModernSettings> windows = new LinkedList<>();
   private final Animation currentCategory = new Animation(300L, Easing.BAKEK_SMALLER);
   private final TextField searchField;
   private final PenisPlayer searchPenis;
   private boolean prevFocused;
   Timer timer = new Timer();
   private final ThemePanel themePanel = new ThemePanel();

   public ModernScreen() {
      float width = 500.0F;
      float height = 343.0F;
      this.menuWindow = new Rect(sr.getScaledWidth() / 2.0F - width / 2.0F, sr.getScaledHeight() / 2.0F - height / 2.0F, width, height);
      this.categories.clear();

      for (MenuCategory category : MenuCategory.values()) {
         LinkedList<ModernModule> filteredModules = new LinkedList<>();
         ModernCategory modern = new ModernCategory(category, filteredModules);

         try {
            modern.setPenis(new PenisPlayer(Rockstar.id("penises/" + category.getName().toLowerCase() + ".penis")));
         } catch (RuntimeException var10) {
         }

         this.categories.add(modern);
         filteredModules.addAll(
            Rockstar.getInstance()
               .getModuleManager()
               .getModules()
               .stream()
               .sorted(Comparator.comparing(Module::getName))
               .filter(module -> module.getCategory().equals(category.getCategory()))
               .map(module -> new ModernModule(module, modern))
               .toList()
         );
      }

      this.searchField = new TextField(Fonts.MEDIUM.getFont(6.0F));
      HashMap<String, FieldAction> append = new HashMap<>();

      for (Module module2 : Rockstar.getInstance().getModuleManager().getModules()) {
         FieldAction action = new FieldAction(
            module2::toggle,
            () -> this.categories
               .forEach(
                  panel -> panel.getModules()
                     .stream()
                     .filter(component -> component.getModule() == module2)
                     .forEach(modernModule -> System.out.println("poka pichego"))
               )
         );
         append.put(module2.getName().replace(" ", ""), action);
         append.put(module2.getName(), action);
      }

      this.searchField.setAppend(append);
      this.searchField.setPreview("Поиск");
      this.searchPenis = new PenisPlayer(Rockstar.id("penises/search.penis"));
      this.searchPenis.stop();
   }

   protected void init() {
      this.closing = false;

      for (ModernCategory category : this.categories) {
         if (category.getPenis() != null) {
            category.getPenis().stop();
         }
      }

      this.themePanel.onInit();
      super.init();
   }

   public void tick() {
      this.handleMovementKeys();
      super.tick();
   }

   @Override
   public void render(UIContext context) {
      this.menuAnimation.update(this.closing ? 0.0F : 1.0F);
      this.menuAnimation.setEasing(!this.closing ? Easing.BAKEK : Easing.BAKEK_BACK);
      this.menuAnimation.setDuration(400L);
      this.scrollHandler.update();
      if (this.drag) {
         this.menuWindow.setX(context.getMouseX() - this.dragX);
         this.menuWindow.setY(context.getMouseY() - this.dragY);
      }

      if (this.searchField.isFocused() && !this.prevFocused) {
         this.searchPenis.playOnce();
      }

      this.prevFocused = this.searchField.isFocused();
      float scroll = (float)(-this.scrollHandler.getValue());
      float alpha = Math.min(1.0F, this.menuAnimation.getValue());

      for (ModernCategory category : this.categories) {
         if (category.getY() - scroll <= -this.scrollHandler.getTargetValue() && this.current != category.getCategory()) {
            this.current = category.getCategory();
         }
      }

      boolean dark = Rockstar.getInstance().getThemeManager().getCurrentTheme() != null && Rockstar.getInstance().getThemeManager().getCurrentTheme().isDark();
      float blurStr = Interface.glassBlur();
      float shiftedBlurStr = Math.max(blurStr - 0.5F, 0.0F);
      float blurKernel = 5.0F + shiftedBlurStr * 10.0F;
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
      RenderUtility.scale(
         context.getMatrices(),
         this.menuWindow.getX() + this.menuWindow.getWidth() / 2.0F,
         this.menuWindow.getY() + this.menuWindow.getHeight() / 2.0F,
         0.5F + 0.5F * this.menuAnimation.getValue()
      );
      context.drawBlurredRect(
         this.menuWindow.getX(),
         this.menuWindow.getY(),
         this.menuWindow.getWidth(),
         this.menuWindow.getHeight(),
         blurKernel,
         5.0F,
         BorderRadius.all(16.0F),
         Colors.WHITE
      );
      context.drawSquircle(
         this.menuWindow.getX(),
         this.menuWindow.getY(),
         this.menuWindow.getWidth(),
         this.menuWindow.getHeight(),
         5.0F,
         BorderRadius.all(16.0F),
         dark ? Colors.getAdditionalColor() : Colors.getBackgroundColor()
      );
      context.drawShadow(
         this.menuWindow.getX() + 5.0F, this.menuWindow.getY() + 5.0F, 109.0F, 333.0F, 20.0F, BorderRadius.all(14.0F), Colors.BLACK.mulAlpha(0.2F)
      );
      context.drawBlurredRect(this.menuWindow.getX() + 5.0F, this.menuWindow.getY() + 5.0F, 109.0F, 333.0F, blurKernel, BorderRadius.all(12.0F), Colors.WHITE);
      context.drawRoundedRect(
         this.menuWindow.getX() + 5.0F,
         this.menuWindow.getY() + 5.0F,
         109.0F,
         333.0F,
         BorderRadius.all(12.0F),
         Colors.getBackgroundColor().mulAlpha(dark ? 0.87F : 0.69F)
      );
      float x = this.menuWindow.getX();
      float y = this.menuWindow.getY();
      float yOff = 0.0F;
      float xOff = 0.0F;
      float moduleWidth = 177.0F;
      context.drawRoundedRect(
         x + 13.0F,
         y + 13.0F,
         93.0F,
         14.0F,
         BorderRadius.all(3.0F),
         dark ? Colors.getAdditionalColor().mulAlpha(0.6F) : Colors.getBackgroundColor().mulAlpha(0.6F)
      );
      DrawUtility.drawAnimationSprite(
         context.getMatrices(), this.searchPenis.getCurrentSprite(), x + 16.0F, y + 16.0F, 8.0F, 8.0F, Colors.getTextColor().mulAlpha(0.5F)
      );
      this.searchField.set(x + 21.0F, y + 13.0F, 80.0F, 14.0F);
      this.searchField.setTextColor(Colors.getTextColor().mulAlpha(0.5F));
      this.searchField.render(context);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
      FontBatching regularBatching = new FontBatching(VertexFormats.POSITION_TEXTURE_COLOR, Fonts.REGULAR);
      context.drawText(Fonts.REGULAR.getFont(6.0F), "Функции", x + 14.0F, y + 35.0F, Colors.getTextColor().mulAlpha(0.3F));
      regularBatching.draw();

      for (ModernCategory modernCategory : this.categories) {
         this.currentCategory.setDuration(150L);
         this.currentCategory.setEasing(Easing.QUAD_OUT);
         if (modernCategory.getCategory() == this.current) {
            this.currentCategory.update(yOff);
         }

         if (GuiUtility.isHovered(x + 12.0F, y + 43.0F + yOff, 95.0, 16.0, context)) {
            CursorUtility.set(CursorType.HAND);
         }

         yOff += 18.0F;
      }

      context.drawSquircle(x + 12.0F, y + 43.0F + this.currentCategory.getValue(), 95.0F, 16.0F, 10.0F, BorderRadius.all(4.0F), Colors.getAccent());
      yOff = 0.0F;
      IconBatching iconBatchingCat = new IconBatching(VertexFormats.POSITION_TEXTURE_COLOR, context.getMatrices());

      for (ModernCategory modernCategory : this.categories) {
         modernCategory.getSelected().update(modernCategory.getCategory() == this.current);
         if (modernCategory.getPenis() == null) {
            context.drawSprite(
               modernCategory.getCategory().getMenuSprite(),
               x + 18.0F,
               y + 47.0F + yOff,
               8.0F,
               8.0F,
               Colors.getIconsColor().mix(Colors.WHITE, modernCategory.getSelected().getValue())
            );
         }

         yOff += 18.0F;
      }

      iconBatchingCat.draw();
      yOff = 0.0F;
      IconBatching iconBatching = new IconBatching(VertexFormats.POSITION_TEXTURE_COLOR, context.getMatrices());

      for (ModernCategory modernCategory : this.categories) {
         modernCategory.getSelected().update(modernCategory.getCategory() == this.current);
         if (modernCategory.getPenis() != null) {
            DrawUtility.drawAnimationSprite(
               context.getMatrices(),
               modernCategory.getPenis().getCurrentSprite(),
               x + 18.0F,
               y + 47.0F + yOff,
               8.0F,
               8.0F,
               Colors.getIconsColor().mix(Colors.WHITE, modernCategory.getSelected().getValue())
            );
         }

         yOff += 18.0F;
      }

      iconBatching.draw();
      yOff = 0.0F;
      FontBatching fontBatching = new FontBatching(VertexFormats.POSITION_TEXTURE_COLOR, Fonts.MEDIUM);

      for (ModernCategory category : this.categories) {
         context.drawText(
            Fonts.MEDIUM.getFont(7.0F),
            category.getCategory().getName(),
            x + 32.0F,
            y + 48.5F + yOff,
            Colors.getTextColor().mix(Colors.WHITE, category.getSelected().getValue())
         );
         yOff += 18.0F;
      }

      fontBatching.draw();
      yOff = scroll;
      float f = scroll;
      ScissorUtility.push(
         context.getMatrices(), this.menuWindow.getX(), this.menuWindow.getY() + 1.0F, this.menuWindow.getWidth(), this.menuWindow.getHeight() - 2.0F
      );
      SquircleBatching squircleBatching = new SquircleBatching(5.0F);

      for (ModernCategory modernCategory : this.categories) {
         float prev = yOff;
         modernCategory.setY(yOff);

         for (ModernModule modernModule : modernCategory.getVisibleModules()) {
            boolean cond = !this.opened(modernModule) && !modernModule.getModule().isHidden();
            modernModule.getVisible().update(cond);
            modernModule.getOffset().update(cond);
            if (!this.visibleCheck(modernModule)) {
               modernModule.set(x + 127.0F + xOff, y + 33.0F + yOff, moduleWidth, 28.0F);
               if (GuiUtility.isHovered(
                  x,
                  y - modernModule.getHeight(),
                  this.menuWindow.getWidth(),
                  this.menuWindow.getHeight() + modernModule.getHeight(),
                  modernModule.getX(),
                  modernModule.getY()
               )) {
                  modernModule.render(context);
                  if (GuiUtility.isHovered(modernModule.getX(), modernModule.getY(), modernModule.getWidth(), modernModule.getHeight(), context)) {
                     CursorUtility.set(CursorType.HAND);
                  }
               }

               if ((xOff += (modernModule.getWidth() + 6.5F) * modernModule.getOffset().getValue()) > this.menuWindow.getWidth() - 139.0F) {
                  yOff += 34.0F * modernModule.getOffset().getValue();
                  xOff = 0.0F;
               }
            }
         }

         if (xOff != 0.0F) {
            yOff += 34.0F;
         }

         xOff = 0.0F;
         yOff += 25.0F;
         if (modernCategory.getCategory() == MenuCategory.OTHER && yOff - prev < this.menuWindow.getHeight()) {
            yOff = prev + this.menuWindow.getHeight();
         }
      }

      squircleBatching.draw();
      RoundedRectBatching roundBatching = new RoundedRectBatching();

      for (ModernCategory category : this.categories) {
         for (ModernModule modernModule : category.getModules()) {
            if (!this.visibleCheck(modernModule)
               && GuiUtility.isHovered(
                  x,
                  y - modernModule.getHeight(),
                  this.menuWindow.getWidth(),
                  this.menuWindow.getHeight() + modernModule.getHeight(),
                  modernModule.getX(),
                  modernModule.getY()
               )) {
               modernModule.renderRounds(context);
            }
         }
      }

      roundBatching.draw();
      RoundedRectBatching roundedRectBatching = new RoundedRectBatching();

      for (ModernCategory modernCategory : this.categories) {
         for (ModernModule module : modernCategory.getModules()) {
            if (!this.visibleCheck(module)
               && GuiUtility.isHovered(
                  x, y - module.getHeight(), this.menuWindow.getWidth(), this.menuWindow.getHeight() + module.getHeight(), module.getX(), module.getY()
               )) {
               module.renderInto(context);
            }
         }
      }

      roundedRectBatching.draw();
      FontBatching mediumBatching = new FontBatching(VertexFormats.POSITION_TEXTURE_COLOR, Fonts.MEDIUM);

      for (ModernCategory modernCategory : this.categories) {
         for (ModernModule modernModule : modernCategory.getVisibleModules()) {
            if (!this.visibleCheck(modernModule)
               && GuiUtility.isHovered(
                  x,
                  y - modernModule.getHeight(),
                  this.menuWindow.getWidth(),
                  this.menuWindow.getHeight() + modernModule.getHeight(),
                  modernModule.getX(),
                  modernModule.getY()
               )) {
               modernModule.renderMedium(context);
            }
         }
      }

      mediumBatching.draw();
      FadeOutBatching fadeOutBatching = new FadeOutBatching(VertexFormats.POSITION_TEXTURE_COLOR, Fonts.REGULAR, 0.9F, 1.0F, moduleWidth - 30.0F, x + 127.0F);

      for (ModernCategory category : this.categories) {
         for (ModernModule modernModule : category.getModules()) {
            if (!this.visibleCheck(modernModule)
               && GuiUtility.isHovered(
                  x,
                  y - modernModule.getHeight(),
                  this.menuWindow.getWidth(),
                  this.menuWindow.getHeight() + modernModule.getHeight(),
                  modernModule.getX(),
                  modernModule.getY()
               )
               && modernModule.getX() == x + 127.0F) {
               modernModule.renderRegular(context);
            }
         }
      }

      fadeOutBatching.draw();
      FadeOutBatching fadeOutBatching2 = new FadeOutBatching(
         VertexFormats.POSITION_TEXTURE_COLOR, Fonts.REGULAR, 0.9F, 1.0F, moduleWidth - 30.0F, x + 127.0F + moduleWidth + 6.5F
      );

      for (ModernCategory modernCategory : this.categories) {
         for (ModernModule modernModule : modernCategory.getVisibleModules()) {
            if (!this.visibleCheck(modernModule)
               && GuiUtility.isHovered(
                  x,
                  y - modernModule.getHeight(),
                  this.menuWindow.getWidth(),
                  this.menuWindow.getHeight() + modernModule.getHeight(),
                  modernModule.getX(),
                  modernModule.getY()
               )
               && modernModule.getX() != x + 127.0F) {
               modernModule.renderRegular(context);
            }
         }
      }

      fadeOutBatching2.draw();
      FontBatching fontBatching2 = new FontBatching(VertexFormats.POSITION_TEXTURE_COLOR, Fonts.SEMIBOLD);

      for (ModernCategory modernCategory : this.categories) {
         if (GuiUtility.isHovered(x, y - 20.0F, this.menuWindow.getWidth(), this.menuWindow.getHeight() + 20.0F, x + 142.0F, y + 16.0F + modernCategory.getY())
            )
          {
            context.drawText(
               Fonts.SEMIBOLD.getFont(12.0F),
               modernCategory.getCategory().getName(),
               x + 143.0F,
               y + 16.0F + modernCategory.getY(),
               Rockstar.getInstance().getThemeManager().getCurrentTheme().getTextColor()
            );
         }
      }

      fontBatching2.draw();
      IconBatching iconBatching2 = new IconBatching(VertexFormats.POSITION_TEXTURE_COLOR, context.getMatrices());

      for (ModernCategory modernCategory : this.categories) {
         if (GuiUtility.isHovered(x, y - 20.0F, this.menuWindow.getWidth(), this.menuWindow.getHeight() + 20.0F, x + 142.0F, y + 16.0F + modernCategory.getY())
            && modernCategory.getPenis() == null) {
            context.drawSprite(
               modernCategory.getCategory().getBigMenuSprite(), x + 129.0F, y + 15.0F + modernCategory.getY(), 10.0F, 10.0F, Colors.getIconsColor()
            );
         }
      }

      iconBatching2.draw();
      IconBatching iconBatching3 = new IconBatching(VertexFormats.POSITION_TEXTURE_COLOR, context.getMatrices());

      for (ModernCategory category : this.categories) {
         if (GuiUtility.isHovered(x, y - 20.0F, this.menuWindow.getWidth(), this.menuWindow.getHeight() + 20.0F, x + 142.0F, y + 16.0F + category.getY())
            && category.getPenis() != null) {
            DrawUtility.drawAnimationSprite(
               context.getMatrices(), category.getPenis().getCurrentSprite(), x + 129.0F, y + 15.0F + category.getY(), 10.0F, 10.0F, Colors.getIconsColor()
            );
         }
      }

      iconBatching3.draw();
      float f2 = yOff - f;
      float visibleHeight = this.menuWindow.getHeight() - 10.0F;
      float maxScroll = -Math.max(0.0F, f2 - visibleHeight);
      this.scrollHandler.setMax(maxScroll - 10.0F);
      ScissorUtility.pop();
      RenderUtility.end(context.getMatrices());
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

      for (ModernSettings window2 : this.windows) {
         window2.render(context);
      }

      if (this.menuAnimation.getValue() > 0.0F) {
         this.themePanel.setMenuAlpha(Math.min(1.0F, this.menuAnimation.getValue()));
         this.themePanel.render(context);
      }

      for (ColorPicker colorPicker2 : this.colorPickers) {
         colorPicker2.render(context);
      }

      this.windows.removeIf(window -> window.getAnimation().getValue() == 0.0F && !window.isShowing());
      this.colorPickers.removeIf(colorPicker -> colorPicker.getAnimation().getValue() == 0.0F && !colorPicker.isShowing());
   }

   private void handleMovementKeys() {
      if (mc.player != null && !this.isTyping()) {
         long windowHandle = mc.getWindow().getHandle();

         for (KeyBinding key : new KeyBinding[]{
            mc.options.forwardKey, mc.options.backKey, mc.options.leftKey, mc.options.rightKey, mc.options.jumpKey
         }) {
            int keyCode = InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey()).getCode();
            key.setPressed(InputUtil.isKeyPressed(windowHandle, keyCode));
         }

         if (mc.player.getAbilities().flying) {
            int keyCode = InputUtil.fromTranslationKey(mc.options.sneakKey.getBoundKeyTranslationKey()).getCode();
            mc.options.sneakKey.setPressed(InputUtil.isKeyPressed(windowHandle, keyCode));
         }
      }
   }

   private boolean isTyping() {
      return mc.currentScreen != null && TextField.LAST_FIELD != null && TextField.LAST_FIELD.isFocused();
   }

   @Override
   public void onMouseClicked(double mouseX, double mouseY, MouseButton button) {
      if (!Rockstar.getInstance().getHud().getIsland().handleClick((float)mouseX, (float)mouseY, button.getButtonIndex())) {
         for (ColorPicker colorPicker : this.colorPickers) {
            boolean isPick = colorPicker.isPick();
            colorPicker.onMouseClicked(mouseX, mouseY, button);
            if (colorPicker.isHovered(mouseX, mouseY) || isPick) {
               return;
            }

            colorPicker.setShowing(false);
         }

         if (this.themePanel.isExpanded() && this.themePanel.isHovered((float)mouseX, (float)mouseY)) {
            this.themePanel.onMouseClicked(mouseX, mouseY, button);
         } else {
            this.themePanel.onMouseClicked(mouseX, mouseY, button);

            for (ModernSettings window : this.windows) {
               window.onMouseClicked(mouseX, mouseY, button);
               if (window.isHovered(mouseX, mouseY)) {
                  return;
               }

               if (!GuiUtility.isHovered(this.menuWindow, mouseX, mouseY)) {
                  boolean can = true;

                  for (ModernSettings window1 : this.windows) {
                     if (GuiUtility.isHovered(window1, mouseX, mouseY)) {
                        can = false;
                     }
                  }

                  if (can) {
                     window.setShowing(false);
                  }
               }
            }

            float x = this.menuWindow.getX();
            float y = this.menuWindow.getY();
            float yOff = 0.0F;
            float xOff = 0.0F;

            for (ModernCategory category : this.categories) {
               if (GuiUtility.isHovered(x + 12.0F, y + 43.0F + yOff, 95.0, 16.0, mouseX, mouseY) && category.getCategory() != this.current) {
                  this.scrollHandler.scroll((-this.scrollHandler.getValue() - (category.getY() - this.scrollHandler.getValue())) / 20.0);
                  if (category.getPenis() != null) {
                     category.getPenis().playOnce();
                  }

                  return;
               }

               yOff += 18.0F;
            }

            for (ModernCategory category : this.categories) {
               for (ModernModule module : category.getModules()) {
                  if (!this.visibleCheck(module)
                     && (GuiUtility.isHovered(this.menuWindow, mouseX, mouseY) || button != MouseButton.LEFT && button != MouseButton.RIGHT)
                     && GuiUtility.isHovered(module.getX(), module.getY(), module.getWidth(), module.getHeight(), mouseX, mouseY)) {
                     module.onMouseClicked(mouseX, mouseY, button);
                     return;
                  }
               }
            }

            if (button != MouseButton.MIDDLE) {
               this.searchField.onMouseClicked(mouseX, mouseY, button);
            }

            if (GuiUtility.isHovered(this.menuWindow, mouseX, mouseY)) {
               this.drag = true;
               this.dragX = (float)(mouseX - this.menuWindow.getX());
               this.dragY = (float)(mouseY - this.menuWindow.getY());
            }

            super.onMouseClicked(mouseX, mouseY, button);
         }
      }
   }

   @Override
   public void onMouseReleased(double mouseX, double mouseY, MouseButton button) {
      this.drag = false;

      for (ModernSettings window : this.windows) {
         window.onMouseReleased(mouseX, mouseY, button);
      }

      for (ColorPicker colorPicker : this.colorPickers) {
         colorPicker.onMouseReleased(mouseX, mouseY, button);
      }

      if (this.searchField.isFocused()) {
         this.searchField.onMouseReleased(mouseX, mouseY, button);
      }

      this.themePanel.onMouseReleased(mouseX, mouseY, button);
      super.onMouseReleased(mouseX, mouseY, button);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      for (ModernSettings window : this.windows) {
         window.onScroll(mouseX, mouseY, horizontalAmount, verticalAmount);
      }

      this.themePanel.onScroll(mouseX, mouseY, horizontalAmount, verticalAmount);
      if (GuiUtility.isHovered(this.menuWindow, mouseX, mouseY)) {
         this.scrollHandler.scroll(verticalAmount);
      }

      return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (!this.searchField.isFocused() && Screen.hasControlDown() && keyCode == 70) {
         this.searchField.setFocused(true);
      }

      this.scrollHandler.onKeyPressed(keyCode);

      for (ModernSettings window : this.windows) {
         window.onKeyPressed(keyCode, scanCode, modifiers);
      }

      for (ColorPicker colorPicker : this.colorPickers) {
         colorPicker.onKeyPressed(keyCode, scanCode, modifiers);
      }

      if (this.searchField.isFocused() && !this.isBindingModule()) {
         this.searchField.onKeyPressed(keyCode, scanCode, modifiers);
      }

      for (ModernCategory category : this.categories) {
         for (ModernModule module : category.getModules()) {
            if (!this.visibleCheck(module)) {
               module.onKeyPressed(keyCode, scanCode, modifiers);
            }
         }
      }

      this.themePanel.onKeyPressed(keyCode, scanCode, modifiers);
      return super.keyPressed(keyCode, scanCode, modifiers);
   }

   public boolean charTyped(char chr, int modifiers) {
      if (this.searchField.isFocused() && !this.isBindingModule()) {
         this.searchField.charTyped(chr, modifiers);
      }

      for (ModernSettings window : this.windows) {
         window.charTyped(chr, modifiers);
      }

      for (ModernCategory category : this.categories) {
         for (ModernModule module : category.getModules()) {
            if (!this.visibleCheck(module)) {
               module.charTyped(chr, modifiers);
            }
         }
      }

      this.themePanel.charTyped(chr, modifiers);
      return super.charTyped(chr, modifiers);
   }

   public void close() {
      this.closing = true;
      Rockstar.getInstance().getModuleManager().getModule(MenuModule.class).disable();
      Sounds soundsModule = Rockstar.getInstance().getModuleManager().getModule(Sounds.class);
      if (soundsModule.isEnabled()) {
         ClientSounds.CLICKGUI_OPEN.play(soundsModule.getVolume().getCurrentValue(), 1.0F);
      }

      Rockstar.getInstance().getFileManager().writeFile("client");
      if (Rockstar.getInstance().getConfigManager().getCurrent() != null) {
         Rockstar.getInstance().getConfigManager().getCurrent().save();
      }

      Rockstar.getInstance().getThemeManager().flushSave();
      if (TextField.LAST_FIELD != null) {
         TextField.LAST_FIELD.setFocused(false);
      }

      super.close();
   }

   private boolean searchCheck(ModernModule component) {
      TextField search = this.searchField;
      return search != null
         && !search.getBuiltText().isBlank()
         && !component.getModule().getName().toLowerCase().contains(search.getBuiltText().toLowerCase())
         && !component.getModule().getName().replace(" ", "").toLowerCase().contains(search.getBuiltText().toLowerCase());
   }

   private boolean visibleCheck(ModernModule component) {
      return component.getOffset().getValue() == 0.0F || this.searchCheck(component) || component.getModule().isHidden();
   }

   private boolean opened(ModernModule component) {
      return this.windows.stream().anyMatch(window -> window.getModule() == component);
   }

   public boolean isBindingModule() {
      return this.categories.stream().flatMap(panel -> panel.getModules().stream()).anyMatch(ModernModule::isBinding);
   }

   public boolean shouldPause() {
      return false;
   }

   public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
   }

   public boolean shouldCloseOnEsc() {
      return true;
   }

   @Generated
   public Rect getMenuWindow() {
      return this.menuWindow;
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
   public boolean isDrag() {
      return this.drag;
   }

   @Generated
   public ScrollHandler getScrollHandler() {
      return this.scrollHandler;
   }

   @Generated
   public MenuCategory getCurrent() {
      return this.current;
   }

   @Generated
   public List<ColorPicker> getColorPickers() {
      return this.colorPickers;
   }

   @Generated
   public List<ModernCategory> getCategories() {
      return this.categories;
   }

   @Generated
   public List<ModernSettings> getWindows() {
      return this.windows;
   }

   @Generated
   public Animation getCurrentCategory() {
      return this.currentCategory;
   }

   @Generated
   public TextField getSearchField() {
      return this.searchField;
   }

   @Generated
   public PenisPlayer getSearchPenis() {
      return this.searchPenis;
   }

   @Generated
   public boolean isPrevFocused() {
      return this.prevFocused;
   }

   @Generated
   public Timer getTimer() {
      return this.timer;
   }

   static {
      new MenuPanel(null);
      new BezierSettingComponent(null, null);
      new BindSettingComponent(null, null);
      new BooleanSettingComponent(null, null);
      new ModeSettingComponent(null, null);
      new ButtonSettingComponent(null, null);
      new ColorSettingComponent(null, null);
      new StringSettingComponent(null, null);
      new RangeSettingComponent(null, null);
      new SliderSettingComponent(null, null);
   }
}
