package moscow.rockstar.mixin.minecraft.client.input;

import com.mojang.brigadier.suggestion.Suggestions;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import moscow.rockstar.Rockstar;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.ChatInputSuggestor.SuggestionWindow;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatInputSuggestor.class)
public abstract class ChatInputSuggestorMixin {
   @Shadow
   @Final
   TextFieldWidget field_21599;
   @Shadow
   private CompletableFuture<Suggestions> field_21611;
   @Shadow
   @Nullable
   private SuggestionWindow field_21612;

   @Shadow
   public abstract void method_23920(boolean var1);

   @Inject(method = "refresh()V", at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/StringReader;canRead()Z", remap = false), cancellable = true)
   private void injectAutoCompletion(CallbackInfo ci) {
      String text = this.field_21599.getText();
      String prefix = Rockstar.getInstance().getCommandManager().getPrefix();
      if (text.startsWith(prefix)) {
         this.field_21611 = Rockstar.getInstance().getCommandManager().autoComplete(text, this.field_21599.getCursor());
         this.field_21611.thenRun(() -> {
            try {
               if (this.field_21611.isDone() && !this.field_21611.get().isEmpty() && this.field_21612 == null) {
                  this.method_23920(false);
                  ci.cancel();
               }
            } catch (ExecutionException | InterruptedException var3x) {
            }
         });
      }
   }
}
