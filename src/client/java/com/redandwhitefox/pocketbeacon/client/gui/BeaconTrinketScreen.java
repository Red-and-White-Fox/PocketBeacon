package com.redandwhitefox.pocketbeacon.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

import com.redandwhitefox.pocketbeacon.PocketBeacon;
import com.redandwhitefox.pocketbeacon.screen.BeaconTrinketScreenHandler;

public class BeaconTrinketScreen extends HandledScreen<BeaconTrinketScreenHandler> {

    private static final Identifier TEXTURE = Identifier.of(PocketBeacon.MOD_ID, "textures/gui/container/beacon_necklace.png");
    private static final Identifier ICONS = Identifier.of(PocketBeacon.MOD_ID, "textures/gui/icons.png");

    private static final List<ItemStack> DISPLAY_BLOCKS = List.of(
            Items.COPPER_BLOCK.getDefaultStack(),
            Items.IRON_BLOCK.getDefaultStack(),
            Items.GOLD_BLOCK.getDefaultStack(),
            Items.EMERALD_BLOCK.getDefaultStack(),
            Items.DIAMOND_BLOCK.getDefaultStack(),
            Items.NETHERITE_BLOCK.getDefaultStack(),
            Items.BEACON.getDefaultStack()
    );

    public BeaconTrinketScreen(BeaconTrinketScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 256;
        this.backgroundHeight = 220;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        // 3 Rows, 4 Columns
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 4; col++) {
                int effectIndex = row * 4 + col;

                // X start moved from 105 to 90
                this.addDrawableChild(new EffectButton(
                        x + 103 + (col * 24), y + 12 + (row * 35), 20, 20,
                        effectIndex, this
                ));
            }
        }

        // Aligned the "X" button to the new grid start (x + 16)
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Clear all"), button -> {
            if (this.client != null && this.client.interactionManager != null) {
                this.client.interactionManager.clickButton(this.handler.syncId, 100);
            }
        }).dimensions(x + 16, y + 100, 75, 15).build());
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;

        context.drawTexture(
                TEXTURE,
                x,                    // Screen X
                y,                    // Screen Y
                this.backgroundWidth, // Width on screen (256)
                this.backgroundHeight,// Height on screen (220)
                0.0f,                 // u: Start X in PNG
                0.0f,                 // v: Start Y in PNG
                1024,                 // regionWidth: Take all 1024 horizontal pixels
                880,                  // regionHeight: Take all 880 vertical pixels
                1024,                 // textureWidth: The actual width of your file
                880                   // textureHeight: The actual height of your file
        );
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        int totalPoints = this.handler.getPropertyDelegate().get(0);
        int charges = this.handler.getPropertyDelegate().get(1);
        int maxEffects = this.handler.getPropertyDelegate().get(2);
        int activeEffects = this.handler.getPropertyDelegate().get(3);

        // Draw the Point Counter (Under the blue triangle)
        context.drawText(this.textRenderer, "Power: " + totalPoints, 18, 35, 0x404040, false);
        context.drawText(this.textRenderer, "Charges: " + charges, 18, 45, 0xFFD700, false);
        context.drawText(this.textRenderer, "Effects: " + activeEffects + "/" + maxEffects, 18, 55, 0x404040, false);

        // Draw the 7 blocks in a 4-column grid
        for (int i = 0; i < DISPLAY_BLOCKS.size(); i++) {
            int row = i / 4;
            int col = i % 4;
            int itemX = 18 + (col * 19); // 19px spacing horizontally
            int itemY = 66 + (row * 17); // 17px spacing vertically

            context.drawItem(DISPLAY_BLOCKS.get(i), itemX, itemY);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);

        int guiX = (this.width - this.backgroundWidth) / 2;
        int guiY = (this.height - this.backgroundHeight) / 2;

        for (int i = 0; i < DISPLAY_BLOCKS.size(); i++) {
            int row = i / 4;
            int col = i % 4;

            int itemX = guiX + 18 + (col * 19);
            int itemY = guiY + 66 + (row * 17);

            if (mouseX >= itemX && mouseX <= itemX + 16 && mouseY >= itemY && mouseY <= itemY + 16) {
                ItemStack stack = DISPLAY_BLOCKS.get(i);

                // Create the custom tooltip lines
                List<Text> tooltip = new java.util.ArrayList<>();
                tooltip.add(stack.getName().copy().formatted(net.minecraft.util.Formatting.GOLD));

                var config = PocketBeacon.CONFIG;
                String uniqueBonus = String.format("Unique: +%.2f Multiplier", config.trinketBeacon.uniqueMultiplier);

                // Add custom point values based on the item
                String pointsText = getPointsForItem(stack.getItem());
                if (!pointsText.isEmpty()) {
                    tooltip.add(Text.literal(pointsText).formatted(net.minecraft.util.Formatting.GRAY));
                    tooltip.add(Text.literal(uniqueBonus).formatted(net.minecraft.util.Formatting.BLUE));
                } else if (stack.isOf(Items.BEACON)) {
                    tooltip.add(Text.literal("Increases Max Active Effects by 1").formatted(net.minecraft.util.Formatting.LIGHT_PURPLE));
                    tooltip.add(Text.literal(uniqueBonus).formatted(net.minecraft.util.Formatting.BLUE));
                } else if (stack.isOf(Items.NETHERITE_BLOCK)) {
                    String nethBonus = String.format("Multiplier: +%.2f per block", config.trinketBeacon.netheriteMultiplier);
                    tooltip.add(Text.literal(nethBonus).formatted(net.minecraft.util.Formatting.DARK_PURPLE));
                    tooltip.add(Text.literal(uniqueBonus).formatted(net.minecraft.util.Formatting.BLUE));
                }

                context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
            }
        }

        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    public MinecraftClient getScreenClient() { return this.client; }
    public BeaconTrinketScreenHandler getHandler() { return this.handler; }

    private class EffectButton extends ButtonWidget {
        private final int effectIndex;
        private final BeaconTrinketScreen screen;

        public EffectButton(int x, int y, int width, int height, int index, BeaconTrinketScreen screen) {
            super(x, y, width, height, Text.empty(), b -> {}, DEFAULT_NARRATION_SUPPLIER);
            this.effectIndex = index;
            this.screen = screen;
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            // 1. Draw the button box (The standard gray Minecraft button)
            super.renderWidget(context, mouseX, mouseY, delta);

            // 2. Draw the Icon
            // Calculation: 3 columns (240 / 80 = 3)
            int u = (this.effectIndex % 3) * 80;
            int v = (this.effectIndex / 3) * 80;

            context.drawTexture(
                    ICONS,
                    this.getX() + 1,    // Screen X: 1px padding inside the button
                    this.getY() + 1,    // Screen Y: 1px padding inside the button
                    18,                 // Width on screen (to fit inside 20x20 button)
                    18,                 // Height on screen
                    (float)u,           // u: start horizontal pixel in PNG
                    (float)v,           // v: start vertical pixel in PNG
                    80,                 // regionWidth: how many pixels wide to take from PNG
                    80,                 // regionHeight: how many pixels tall to take from PNG
                    240,                // textureWidth: total width of your PNG file
                    320                 // textureHeight: total height of your PNG file
            );

            // 3. Draw the Level Number
            int level = screen.getHandler().getLevelForIndex(this.effectIndex);
            
            if (level > 0) {
                String levelText = String.valueOf(level);

                // Access the field directly: screen.textRenderer
                int textWidth = screen.textRenderer.getWidth(levelText);

                context.drawText(
                        screen.textRenderer,
                        levelText,
                        this.getX() + (this.width / 2) - (textWidth / 2),
                        this.getY() + this.height + 2, // 2 pixels below the button
                        0xFFFFFF,
                        true
                );
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.active && this.visible && this.clicked(mouseX, mouseY)) {
                int packetId = (button == 1) ? (this.effectIndex + 50) : this.effectIndex;
                
                if (screen.getScreenClient().interactionManager != null) {
                    screen.getScreenClient().interactionManager.clickButton(screen.getHandler().syncId, packetId);
                    this.playDownSound(screen.getScreenClient().getSoundManager());
                }
                
                return true;
            }
            
            return false;
        }
    }

    private String getPointsForItem(net.minecraft.item.Item item) {
        // Access the static config instance from your main mod class
        var config = PocketBeacon.CONFIG;

        if (item == Items.COPPER_BLOCK) return "Value: " + (int)config.trinketBeacon.copperValue + " pts";
        if (item == Items.IRON_BLOCK)   return "Value: " + (int)config.trinketBeacon.ironValue + " pts";
        if (item == Items.GOLD_BLOCK)   return "Value: " + (int)config.trinketBeacon.goldValue + " pts";
        if (item == Items.EMERALD_BLOCK) return "Value: " + (int)config.trinketBeacon.emeraldValue + " pts";
        if (item == Items.DIAMOND_BLOCK) return "Value: " + (int)config.trinketBeacon.diamondValue + " pts";

        return "";
    }
}
