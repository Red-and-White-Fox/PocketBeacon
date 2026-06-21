package com.redandwhitefox.pocketbeacon.screen;

import java.util.HashSet;
import java.util.Set;

import com.redandwhitefox.pocketbeacon.PocketBeacon;
import com.redandwhitefox.pocketbeacon.item.BeaconTrinketItem;

import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.Items;
import net.minecraft.util.collection.DefaultedList;

public class BeaconTrinketScreenHandler extends ScreenHandler {
    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;
    private final ItemStack trinketStack;
    private final PlayerInventory playerInventory;
    private boolean isSyncing = false;

    // This constructor is called on the CLIENT when opening the screen
    public BeaconTrinketScreenHandler(int syncId, PlayerInventory playerInv, ItemStack stack) {
        this(syncId, playerInv,
                new SimpleInventory(BeaconTrinketItem.getInventory(stack, playerInv.player.getWorld().getRegistryManager()).toArray(new ItemStack[0])),
                new ArrayPropertyDelegate(4), stack);
    }

    // CONSTRUCTOR 2: The actual logic
    public BeaconTrinketScreenHandler(int syncId, PlayerInventory playerInv, Inventory inventory, PropertyDelegate delegate, ItemStack stack) {
        super(PocketBeacon.BEACON_TRINKET_SCREEN_HANDLER, syncId);
        checkSize(inventory, 12);
        checkDataCount(delegate, 4);

        this.inventory = inventory;
        this.propertyDelegate = delegate;
        this.trinketStack = stack;
        this.playerInventory = playerInv;

        if (inventory instanceof SimpleInventory simpleInventory) {
            simpleInventory.addListener(this::onContentChanged);
        }

        inventory.onOpen(playerInv.player);
        this.addProperties(delegate);

        // 1. Trinket Inventory Slots (6 rows, 2 columns)
        for (int row = 0; row < 6; ++row) {
            for (int col = 0; col < 2; ++col) {
                int index = col + (row * 2);
                this.addSlot(new TrinketSlot(inventory, index, 207 + col * 18, 11 + row * 18));
            }
        }

        // 2. Player Inventory & Hotbar
        int invX = 40;
        int invY = 130;
        addPlayerInventory(playerInv, invX, invY);
        addPlayerHotbar(playerInv, invX, invY + 58);

        // Initial Calculation to sync points on open
        this.sendContentUpdates();
        syncBeaconData();
    }


    @Override
    public void onContentChanged(Inventory inventory) {
        // If we are currently in the middle of a save, ignore further change events
        if (isSyncing) return;

        super.onContentChanged(inventory);

        if (this.playerInventory == null || this.playerInventory.player.getWorld().isClient()) return;
        
        isSyncing = true;
        
        try {
            DefaultedList<ItemStack> items = DefaultedList.ofSize(12, ItemStack.EMPTY);
            
            for (int i = 0; i < 12; i++) {
                items.set(i, this.inventory.getStack(i));
            }

            BeaconTrinketItem.saveInventory(
                    this.trinketStack,
                    items,
                    this.playerInventory.player.getWorld().getRegistryManager()
            );

            syncBeaconData();
        } catch (Exception e) {
        	PocketBeacon.LOGGER.error("Failed to sync data", e);
        } finally {
            isSyncing = false;
        }
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (player.getWorld().isClient) return true;
        boolean changed = false;
        int availableCharges = this.propertyDelegate.get(1);

        // 1. UPGRADE Logic (0-11)
        if (id >= 0 && id <= 11) {
            String effectName = getEffectNameFromIndex(id);
            
            if (effectName.equals("empty")) return false;

            int currentLevel = BeaconTrinketItem.getEffectLevel(trinketStack, effectName);

            if (availableCharges > 0 && currentLevel < 4) {
                //Check if this is a NEW effect activation
                if (currentLevel == 0 && getCurrentlyActiveCount() >= getMaxActiveEffects()) {
                    return false;
                }

                int usedCharges = BeaconTrinketItem.getEffectLevel(trinketStack, "used_charges");
                BeaconTrinketItem.setEffectLevel(trinketStack, effectName, currentLevel + 1);
                BeaconTrinketItem.setEffectLevel(trinketStack, "used_charges", usedCharges + 1);
                changed = true;
            }
        }
        
        // 2. DOWNGRADE Logic (50-61)
        else if (id >= 50 && id <= 61) {
            String effectName = getEffectNameFromIndex(id - 50);
            int currentLevel = BeaconTrinketItem.getEffectLevel(trinketStack, effectName);

            if (currentLevel > 0) {
                int usedCharges = BeaconTrinketItem.getEffectLevel(trinketStack, "used_charges");

                BeaconTrinketItem.setEffectLevel(trinketStack, effectName, currentLevel - 1);
                BeaconTrinketItem.setEffectLevel(trinketStack, "used_charges", usedCharges - 1);

                changed = true;
            }
        }
        
        // 3. CLEAR ALL Logic (100)
        else if (id == 100) {
            for (int i = 0; i <= 11; i++) {
                BeaconTrinketItem.setEffectLevel(trinketStack, getEffectNameFromIndex(i), 0);
            }
            
            BeaconTrinketItem.setEffectLevel(trinketStack, "used_charges", 0);

            changed = true;
        }
        
        if (changed) {
            syncBeaconData();
            this.sendContentUpdates(); // Force sync to client slots
            return true;
        }

        return super.onButtonClick(player, id);
    }

    private String getEffectNameFromIndex(int index) {
        return switch(index) {
            case 0 -> "speed";
            case 1 -> "haste";
            case 2 -> "resistance";
            case 3 -> "jump_boost";
            case 4 -> "strength";
            case 5 -> "regeneration";
            case 6 -> "saturation";
            case 7 -> "dolphins_grace";
            case 8 -> "reach";
            case 9 -> "knockback_res";
            case 10 -> "vacuum";
            default -> "empty";
        };
    }

    /**
     * Handles point calculation, charge calculation, and NBT saving.
     */
    private void syncBeaconData() {
        int points = (int) calculatePoints();
        int totalCharges = calculateCharges(points);

        // Read the current NBT value
        int usedCharges = BeaconTrinketItem.getEffectLevel(trinketStack, "used_charges");

        if (!this.playerInventory.player.getWorld().isClient()) {
            // If the player removed blocks, they lose their spent charges/upgrades
            if (usedCharges > totalCharges) {
                usedCharges = totalCharges; // Cap it at the new max
                // IMPORTANT: Save this back to the item NBT immediately
                BeaconTrinketItem.setEffectLevel(trinketStack, "used_charges", usedCharges);

                // Optional: You should also loop through your effects
                // and lower them if usedCharges was forced down.
            }
        }

        this.propertyDelegate.set(0, points);
        // UI shows: (Max possible with current blocks) - (What we already spent)
        this.propertyDelegate.set(1, totalCharges - usedCharges);
        this.propertyDelegate.set(2, getMaxActiveEffects());
        this.propertyDelegate.set(3, getCurrentlyActiveCount());
    }

    public double calculatePoints() {
        double basePoints = 0;
        double multiplier = 1.0;
        Set<Item> uniqueBlocks = new HashSet<>();

        for (int i = 0; i < 12; i++) {
            ItemStack stack = this.inventory.getStack(i);
            
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();
            uniqueBlocks.add(item);

            double val = 0;
            
            if      (item == Items.COPPER_BLOCK)  val = PocketBeacon.CONFIG.trinketBeacon.copperValue;
            else if (item == Items.IRON_BLOCK)    val = PocketBeacon.CONFIG.trinketBeacon.ironValue;
            else if (item == Items.GOLD_BLOCK)    val = PocketBeacon.CONFIG.trinketBeacon.goldValue;
            else if (item == Items.EMERALD_BLOCK) val = PocketBeacon.CONFIG.trinketBeacon.emeraldValue;
            else if (item == Items.DIAMOND_BLOCK) val = PocketBeacon.CONFIG.trinketBeacon.diamondValue;

            basePoints += (val * stack.getCount());

            if (item == Items.NETHERITE_BLOCK) {
                multiplier += (PocketBeacon.CONFIG.trinketBeacon.netheriteMultiplier * stack.getCount());
            }
        }

        // Only count unique blocks for the valuable 6 types
        multiplier += (uniqueBlocks.size() * PocketBeacon.CONFIG.trinketBeacon.uniqueMultiplier);

        // Safety cap for the "Short" overflow
        return Math.min(basePoints * multiplier, 32767.0);
    }

    public int calculateCharges(int points) {
        int cost = 650;
        int charges = 0;
        
        while (points >= cost) {
            points -= cost;
            cost += 100;
            charges++;
            
            if (charges >= 20) break;
        }
        
        return charges;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            newStack = originalStack.copy();

            if (index < 12) {
                // FROM Trinket TO Player Inventory (slots 12 to 47)
                if (!this.insertItem(originalStack, 12, 48, true)) {
                    return ItemStack.EMPTY; // Returns empty if player inv is full
                }
            } else {
                // FROM Player TO Trinket Inventory (slots 0 to 11)
                if (TrinketSlot.isAllowed(originalStack)) {
                    if (!this.insertItem(originalStack, 0, 12, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    return ItemStack.EMPTY;
                }
            }

            if (originalStack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }

            slot.onTakeItem(player, originalStack);
        }
        return newStack;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    public PropertyDelegate getPropertyDelegate() {
        return this.propertyDelegate;
    }

    private void addPlayerInventory(PlayerInventory inv, int x, int y) {
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, x + col * 18, y + row * 18));
            }
        }
    }

    private void addPlayerHotbar(PlayerInventory inv, int x, int y) {
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(inv, col, x + col * 18, y));
        }
    }

    public int getLevelForIndex(int index) {
        String name = getEffectNameFromIndex(index);
        if (name.equals("empty")) return 0;

        // 1. Identify which stack we should actually be reading from.
        // We want the "Live" stack that Minecraft is currently syncing.
        ItemStack stackToRead = this.trinketStack;

        if (!this.playerInventory.player.getWorld().isClient()) return BeaconTrinketItem.getEffectLevel(stackToRead, name);
        
        // CASE A: Check Trinket Slots (Synced by Trinkets API)
        var component = TrinketsApi.getTrinketComponent(this.playerInventory.player);
        
        if (!component.isPresent()) return BeaconTrinketItem.getEffectLevel(stackToRead, name);
        
        var equipped = component.get().getEquipped(stack -> stack.getItem() instanceof BeaconTrinketItem);
        
        if (!equipped.isEmpty()) {
            stackToRead = equipped.get(0).getRight();
        } else {
            // CASE B: The item is in the hand/hotbar.
            // We MUST find the stack inside the ScreenHandler's slots.
            // These slots are automatically updated by the server's 'sendContentUpdates()'.
            for (Slot slot : this.slots) {
                ItemStack slotStack = slot.getStack();
                
                if (slotStack.getItem() instanceof BeaconTrinketItem) {
                    stackToRead = slotStack;
                    break;
                }
            }
        }
        
        return BeaconTrinketItem.getEffectLevel(stackToRead, name);
    }

    /**
     * Calculates how many DIFFERENT effects the player can have active.
     * Base is 1, +1 for every Beacon in the internal inventory.
     */
    public int getMaxActiveEffects() {
        int beaconCount = 0;
        
        for (int i = 0; i < 12; i++) {
            ItemStack stack = this.inventory.getStack(i);
            
            if (!stack.isEmpty() && stack.isOf(Items.BEACON)) {
                beaconCount += stack.getCount();
            }
        }
        
        return 1 + beaconCount;
    }

    /**
     * Counts how many effects currently have a level > 0.
     */
    public int getCurrentlyActiveCount() {
        int active = 0;
        // Check all possible effects (0 to 10)
        for (int i = 0; i <= 11; i++) {
            String effectName = getEffectNameFromIndex(i);
            
            if (!effectName.equals("empty") && BeaconTrinketItem.getEffectLevel(trinketStack, effectName) > 0) {
                active++;
            }
        }
        return active;
    }

    private static class TrinketSlot extends Slot {
        public TrinketSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return isAllowed(stack);
        }

        public static boolean isAllowed(ItemStack stack) {
            Item item = stack.getItem();
            
            return item == Items.COPPER_BLOCK  || item == Items.IRON_BLOCK ||
                    item == Items.GOLD_BLOCK   || item == Items.EMERALD_BLOCK ||
                    item == Items.DIAMOND_BLOCK|| item == Items.NETHERITE_BLOCK ||
                    item == Items.BEACON;
        }
    }
}
