package com.redandwhitefox.pocketbeacon;

import dev.emi.trinkets.api.TrinketsApi;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.loot.condition.RandomChanceWithEnchantedBonusLootCondition;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.SetCountLootFunction;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redandwhitefox.pocketbeacon.config.PocketBeaconConfig;
import com.redandwhitefox.pocketbeacon.item.BeaconMedaillon;
import com.redandwhitefox.pocketbeacon.item.BeaconTrinketItem;
import com.redandwhitefox.pocketbeacon.item.DragonLeather;
import com.redandwhitefox.pocketbeacon.network.OpenBeaconTrinketPayload;
import com.redandwhitefox.pocketbeacon.screen.BeaconTrinketScreenHandler;


public class PocketBeacon implements ModInitializer {
	public static final String MOD_ID = "pocketbeacon";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final BeaconTrinketItem BEACON_TRINKET = new BeaconTrinketItem(new Item.Settings().maxCount(1));
	public static final DragonLeather DRAGON_LEATHER = new DragonLeather(new Item.Settings().rarity(Rarity.EPIC));
	public static final BeaconMedaillon BEACON_MEDAILLON = new BeaconMedaillon(new Item.Settings().maxCount(1));
	
	public static final ScreenHandlerType<BeaconTrinketScreenHandler> BEACON_TRINKET_SCREEN_HANDLER =
			Registry.register(
					Registries.SCREEN_HANDLER,
					Identifier.of(MOD_ID, "beacon_trinket"),
					new ExtendedScreenHandlerType<>(BeaconTrinketScreenHandler::new, ItemStack.PACKET_CODEC)
					);
	
	public static PocketBeaconConfig CONFIG;

	@Override
	public void onInitialize() {
		// CONFIG
		ConfigHolder<PocketBeaconConfig> configHolder = AutoConfig.register(PocketBeaconConfig.class, (config, clazz) -> new GsonConfigSerializer<>(config, clazz));
		CONFIG = configHolder.getConfig();
		configHolder.registerSaveListener((holder, config) -> {
			return ActionResult.SUCCESS;
		});
		
		// ITEM REGISTRY
		Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "dragon_leather"), DRAGON_LEATHER);
		Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "beacon_medaillon"), BEACON_MEDAILLON);
		Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "beacon_trinket"), BEACON_TRINKET);

		// LOOT TABLE REGISTRY
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			if (EntityType.ENDER_DRAGON.getLootTableId().equals(key)) {
				LootPool.Builder poolBuilder = LootPool.builder()
						.rolls(ConstantLootNumberProvider.create(1))
						.conditionally(RandomChanceWithEnchantedBonusLootCondition.builder(
								registries,
								0.30f, // Arg 2: Base chance without enchantment (0.30f = 30%)
								0.14f  // Arg 3: Bonus per level (0.14 = 14%) -> Lvl 5 = 30% + (5*14)% = 100%
						))
						.with(ItemEntry.builder(DRAGON_LEATHER).weight(60)
								.apply(SetCountLootFunction.builder(ConstantLootNumberProvider.create(1.0f))))
						.with(ItemEntry.builder(DRAGON_LEATHER).weight(30)
								.apply(SetCountLootFunction.builder(ConstantLootNumberProvider.create(2.0f))))
						.with(ItemEntry.builder(DRAGON_LEATHER).weight(10)
								.apply(SetCountLootFunction.builder(ConstantLootNumberProvider.create(3.0f))));

				tableBuilder.pool(poolBuilder);
			}
		});

		// NETWORKING
		PayloadTypeRegistry.playC2S().register(OpenBeaconTrinketPayload.ID, OpenBeaconTrinketPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(OpenBeaconTrinketPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayerEntity player = context.player();
				ItemStack stack = ItemStack.EMPTY;

				// 1. Check Trinket Slot
				var trinketComp = TrinketsApi.getTrinketComponent(player);
				
				if (trinketComp.isPresent()) {
					var equipped = trinketComp.get().getEquipped(s -> s.getItem() instanceof BeaconTrinketItem);
					
					if (!equipped.isEmpty()) {
						stack = equipped.get(0).getRight();
					}
				}

				// 2. If not in Trinket slot, check Hands (Important for Right-Click logic!)
				if (stack.isEmpty()) {
					if (player.getMainHandStack().getItem() instanceof BeaconTrinketItem) {
						stack = player.getMainHandStack();
					} else if (player.getOffHandStack().getItem() instanceof BeaconTrinketItem) {
						stack = player.getOffHandStack();
					}
				}

				if (!stack.isEmpty()) {
					final ItemStack finalStack = stack; // Must be final for the anonymous class
					player.openHandledScreen(new ExtendedScreenHandlerFactory<ItemStack>() {
						@Override public ItemStack getScreenOpeningData(ServerPlayerEntity player) { return finalStack; }
						@Override public Text getDisplayName() { return Text.literal("Beacon Necklace"); }
						@Override public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
							return new BeaconTrinketScreenHandler(syncId, inv, finalStack);
						}
					});
				}
			});
		});
	}
}
