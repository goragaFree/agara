package moscow.rockstar.systems.modules.modules.visuals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.Generated;
import moscow.rockstar.Rockstar;
import moscow.rockstar.systems.event.EventListener;
import moscow.rockstar.systems.event.impl.render.HudRenderEvent;
import moscow.rockstar.systems.localization.Language;
import moscow.rockstar.systems.localization.Localizator;
import moscow.rockstar.systems.modules.api.ModuleCategory;
import moscow.rockstar.systems.modules.api.ModuleInfo;
import moscow.rockstar.systems.modules.impl.BaseModule;
import moscow.rockstar.systems.setting.settings.ModeSetting;
import moscow.rockstar.systems.setting.settings.SliderSetting;
import moscow.rockstar.systems.theme.Theme;
import moscow.rockstar.utility.animation.base.Animation;
import moscow.rockstar.utility.animation.base.Easing;

@ModuleInfo(name = "Interface", category = ModuleCategory.VISUALS, enabledByDefault = true)
public class Interface extends BaseModule {
   private final ModeSetting mode = new ModeSetting(this, "modules.settings.interface.mode");
   private final ModeSetting.Value liquidGlass = new ModeSetting.Value(this.mode, "modules.settings.interface.liquidGlass");
   private final ModeSetting.Value minimalism = new ModeSetting.Value(this.mode, "modules.settings.interface.minimalism");
   private final ModeSetting themeMode = new ModeSetting(this, "modules.settings.interface.theme", this.liquidGlass::isSelected);
   public final ModeSetting.Value dark = new ModeSetting.Value(this.themeMode, "modules.settings.interface.dark");
   public final ModeSetting.Value light = new ModeSetting.Value(this.themeMode, "modules.settings.interface.light");
   private final SliderSetting glassOpacity = new SliderSetting(this, "theme.opacity", () -> !this.liquidGlass.isSelected())
      .min(0.0F)
      .max(100.0F)
      .step(1.0F)
      .currentValue(20.0F)
      .suffix("%");
   private final SliderSetting glassStrength = new SliderSetting(this, "theme.strength", () -> !this.liquidGlass.isSelected())
      .min(0.0F)
      .max(100.0F)
      .step(1.0F)
      .currentValue(25.0F);
   private final SliderSetting glassDistortion = new SliderSetting(this, "theme.distortion", () -> !this.liquidGlass.isSelected())
      .min(-0.2F)
      .max(0.2F)
      .step(0.01F)
      .currentValue(0.08F);
   private final SliderSetting glassBlur = new SliderSetting(this, "theme.blur_strength", () -> !this.liquidGlass.isSelected())
      .min(0.0F)
      .max(8.0F)
      .step(0.25F)
      .currentValue(0.5F);
   private final SliderSetting glassRounding = new SliderSetting(this, "theme.hud_rounding", () -> !this.liquidGlass.isSelected())
      .min(0.0F)
      .max(8.0F)
      .step(1.0F)
      .currentValue(7.0F);
   private final ModeSetting language = new ModeSetting(this, "modules.settings.interface.language");
   private final Animation liquidGlassAnim = new Animation(500L, Easing.BOTH_CUBIC);
   private final ExecutorService executor = Executors.newSingleThreadExecutor();
   private boolean languageAutoDetected;
   private int lastLang = 0;
   private final EventListener<HudRenderEvent> onHudRenderEvent = event -> {
      this.liquidGlassAnim.setEasing(Easing.FIGMA_EASE_IN_OUT);
      this.liquidGlassAnim.update(this.liquidGlass.isSelected());
      int lang = this.language.getValues().indexOf(this.language.getValue());
      if (lang != this.lastLang) {
         Localizator.setLanguage(lang == 0 ? Language.RU_RU : (lang == 1 ? Language.EN_US : (lang == 2 ? Language.UK_UA : Language.PL_PL)));
         this.languageAutoDetected = false;
      }

      this.lastLang = lang;
      Rockstar.getInstance().getThemeManager().setCurrentTheme(this.dark.isSelected() ? Theme.DARK : Theme.LIGHT);
   };

   public Interface() {
      new ModeSetting.Value(this.language, "Русский");
      new ModeSetting.Value(this.language, "English");
      new ModeSetting.Value(this.language, "Українська");
      new ModeSetting.Value(this.language, "polski");
   }

   private void detectLanguageByIP() {
      this.executor.submit(() -> {
         try {
            String countryCode = this.getCountryCodeByIP();
            if (countryCode != null) {
               String var2 = countryCode.toUpperCase();
               switch (var2) {
                  case "UA":
                     this.language.setValue(this.language.getValues().get(2));
                     Localizator.setLanguage(Language.UK_UA);
                     break;
                  case "PL":
                     this.language.setValue(this.language.getValues().get(3));
                     Localizator.setLanguage(Language.PL_PL);
                     break;
                  default:
                     this.language.setValue((ModeSetting.Value)this.language.getValues().getFirst());
                     Localizator.setLanguage(Language.RU_RU);
               }

               this.languageAutoDetected = true;
               this.lastLang = this.language.getValues().indexOf(this.language.getValue());
            }
         } catch (Exception var4) {
            Rockstar.LOGGER.error("Failed to detect language by IP", var4);
         }
      });
   }

   private String getCountryCodeByIP() throws IOException {
      URL url = new URL("http://ip-api.com/json/?fields=countryCode");
      HttpURLConnection connection = (HttpURLConnection)url.openConnection();
      connection.setRequestMethod("GET");

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
         StringBuilder response = new StringBuilder();

         String line;
         while ((line = reader.readLine()) != null) {
            response.append(line);
         }

         String json = response.toString();
         int start = json.indexOf("countryCode\":\"") + 14;
         if (start >= 14) {
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
         }
      }

      return null;
   }

   public static boolean glassSelected() {
      return Rockstar.getInstance().getModuleManager().getModule(Interface.class).liquidGlass.isSelected();
   }

   public static float glass() {
      return Rockstar.getInstance().getModuleManager().getModule(Interface.class).liquidGlassAnim.getValue();
   }

   public static float minimalizm() {
      return 1.0F - glass();
   }

   public static boolean showGlass() {
      return glass() > 0.0F;
   }

   public static boolean showMinimalizm() {
      return glass() < 1.0F;
   }

   private static Interface module() {
      return Rockstar.getInstance().getModuleManager().getModule(Interface.class);
   }

   public static float glassOpacity() {
      try {
         return module().glassOpacity.getCurrentValue();
      } catch (Exception e) {
         return 20.0F;
      }
   }

   public static float glassStrength() {
      try {
         return module().glassStrength.getCurrentValue();
      } catch (Exception e) {
         return 25.0F;
      }
   }

   public static float glassDistortion() {
      try {
         return module().glassDistortion.getCurrentValue();
      } catch (Exception e) {
         return 0.08F;
      }
   }

   public static float glassBlur() {
      try {
         return module().glassBlur.getCurrentValue();
      } catch (Exception e) {
         return 0.5F;
      }
   }

   public static float glassRounding() {
      try {
         return module().glassRounding.getCurrentValue();
      } catch (Exception e) {
         return 7.0F;
      }
   }

   @Generated
   public ModeSetting getMode() {
      return this.mode;
   }

   @Generated
   public ModeSetting.Value getLiquidGlass() {
      return this.liquidGlass;
   }

   @Generated
   public ModeSetting.Value getMinimalism() {
      return this.minimalism;
   }

   @Generated
   public ModeSetting getThemeMode() {
      return this.themeMode;
   }

   @Generated
   public ModeSetting.Value getDark() {
      return this.dark;
   }

   @Generated
   public ModeSetting.Value getLight() {
      return this.light;
   }

   @Generated
   public ModeSetting getLanguage() {
      return this.language;
   }

   @Generated
   public Animation getLiquidGlassAnim() {
      return this.liquidGlassAnim;
   }

   @Generated
   public ExecutorService getExecutor() {
      return this.executor;
   }

   @Generated
   public boolean isLanguageAutoDetected() {
      return this.languageAutoDetected;
   }

   @Generated
   public int getLastLang() {
      return this.lastLang;
   }

   @Generated
   public EventListener<HudRenderEvent> getOnHudRenderEvent() {
      return this.onHudRenderEvent;
   }
}
