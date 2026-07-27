package moscow.rockstar.mixin.minecraft.client.gui.screen;

import moscow.rockstar.Rockstar;
import moscow.rockstar.framework.base.CustomDrawContext;
import moscow.rockstar.systems.event.impl.render.ChatRenderEvent;
import moscow.rockstar.systems.event.impl.render.HudRenderEvent;
import moscow.rockstar.systems.event.impl.window.ChatClickEvent;
import moscow.rockstar.systems.event.impl.window.ChatReleaseEvent;
import moscow.rockstar.utility.interfaces.IMinecraft;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class ChatScreenMixin extends Screen implements IMinecraft {
   @Shadow
   protected TextFieldWidget field_2382;
   @Shadow
   private ChatInputSuggestor field_21616;

   protected ChatScreenMixin(Text title) {
      super(title);
   }

   @Inject(method = "sendMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
   private void onSendMessage(String text, boolean addToHistory, CallbackInfo ci) {
      if (Rockstar.getInstance().getCommandManager().dispatch(text)) {
         mc.inGameHud.getChatHud().addToMessageHistory(text);
         ci.cancel();
      }
   }

   @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("RETURN"))
   public void render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      CustomDrawContext customCtx = CustomDrawContext.of(context);
      Rockstar.getInstance().getEventManager().triggerEvent(new HudRenderEvent(customCtx, delta));
      Rockstar.getInstance().getEventManager().triggerEvent(new ChatRenderEvent(customCtx, delta));
   }

   @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"))
   private void onMouseClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
      Rockstar.getInstance().getEventManager().triggerEvent(new ChatClickEvent((float)mouseX, (float)mouseY, button));
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      Rockstar.getInstance().getEventManager().triggerEvent(new ChatReleaseEvent((float)mouseX, (float)mouseY, button));
      return super.mouseReleased(mouseX, mouseY, button);
   }
}
