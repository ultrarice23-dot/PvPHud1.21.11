package dev.pvphud.mixin;

import dev.pvphud.tracker.CombatTracker;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    /** HEAD = before the vanilla attack-cooldown reset, so we read the real charge level. */
    @Inject(method = "attackEntity", at = @At("HEAD"), require = 0)
    private void pvphud$onAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        CombatTracker.I.onAttack(player, target);
    }
}
