package com.redandwhitefox.pocketbeacon.mixin;

import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.redandwhitefox.pocketbeacon.PocketBeacon;
import com.redandwhitefox.pocketbeacon.item.BeaconTrinketItem;

import java.util.List;

@Mixin(BeaconBlockEntity.class)
public abstract class BeaconBlockEntityMixin {

    @Inject(method = "applyPlayerEffects", at = @At("TAIL"))
    private static void onApplyPlayerEffects(World world, BlockPos pos, int beaconLevel, RegistryEntry<StatusEffect> primary, RegistryEntry<StatusEffect> secondary, CallbackInfo ci) {
        if (world.isClient) return;

        int duration = (9 + beaconLevel * 2) * 20;
        List<Double> ranges = PocketBeacon.CONFIG.vanillaBeacon.beaconRanges;
        double range = ranges.getLast();        
        
        if (beaconLevel > 0 && beaconLevel <= ranges.size()) {
        	range = ranges.get(beaconLevel - 1);
        }
        
        PocketBeacon.LOGGER.info("Range: " + range);
        Box box = (new Box(pos)).expand(range).stretch(0.0, (double)world.getHeight(), 0.0);

        List<PlayerEntity> players = world.getNonSpectatingEntities(PlayerEntity.class, box);

        for (PlayerEntity player : players) {
            TrinketsApi.getTrinketComponent(player).ifPresent(component -> {
                var necklace = component.getEquipped(itemStack -> itemStack.getItem() instanceof BeaconTrinketItem);

                if (necklace.isEmpty()) return;
                
                ItemStack stack = necklace.get(0).getRight();
                applyNecklaceEffects(player, stack, duration);
            });
        }
    }

    @ModifyConstant(method = "updateLevel", constant = @Constant(intValue = 4))
    private static int increaseMaxLevel(int original) {
        return PocketBeacon.CONFIG.vanillaBeacon.beaconRanges.size();
    }

    private static void applyNecklaceEffects(PlayerEntity player, ItemStack stack, int duration) {
        // Standard Vanilla Effects
        BeaconTrinketItem.applyEffect(player, stack, "haste", StatusEffects.HASTE, duration);
        BeaconTrinketItem.applyEffect(player, stack, "speed", StatusEffects.SPEED, duration);
        BeaconTrinketItem.applyEffect(player, stack, "strength", StatusEffects.STRENGTH, duration);
        BeaconTrinketItem.applyEffect(player, stack, "resistance", StatusEffects.RESISTANCE, duration);
        BeaconTrinketItem.applyEffect(player, stack, "jump_boost", StatusEffects.JUMP_BOOST, duration);
        BeaconTrinketItem.applyEffect(player, stack, "regeneration", StatusEffects.REGENERATION, duration);

        // New Standard Effects
        BeaconTrinketItem.applyEffect(player, stack, "saturation", StatusEffects.SATURATION, duration);
        BeaconTrinketItem.applyEffect(player, stack, "dolphins_grace", StatusEffects.DOLPHINS_GRACE, duration);

        // Special Attribute-based Effects
        BeaconTrinketItem.applySpecialAttributes(player, stack, duration);
    }
}
