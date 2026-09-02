package com.vizor.optirec.mixin;

import com.vizor.optirec.CaptureManager;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "render(FJZ)V", at = @At("RETURN"))
    private void optirec$captureFrame(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
        // Вызывается ПОСЛЕ отрисовки всего кадра (мир + GUI + чат + экраны),
        // поэтому захватывает полный экран, включая HUD/чат/меню.
        CaptureManager.capture();
    }
}
