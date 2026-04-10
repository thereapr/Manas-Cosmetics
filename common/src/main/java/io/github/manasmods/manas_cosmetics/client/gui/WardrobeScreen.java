package io.github.manasmods.manas_cosmetics.client.gui;

import io.github.manasmods.manas_cosmetics.api.CosmeticDefinition;
import io.github.manasmods.manas_cosmetics.api.CosmeticSlot;
import io.github.manasmods.manas_cosmetics.client.ClientCosmeticState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The wardrobe GUI, opened with Left-Alt or /manas_cosmetics wardrobe.
 *
 * Layout:
 *  ┌──────────────────────────────────────────────────┐
 *  │  [Slot tabs]                    [Preset panel]   │
 *  │  ┌──────────────────────┐   ┌──────────────────┐ │
 *  │  │  Cosmetic list        │   │ Preset 1         │ │
 *  │  │  ☑ Icicle Wings [X]   │   │ Preset 2         │ │
 *  │  │  ○ Dragon Cape  [✓]   │   │  ...             │ │
 *  │  │                       │   │ [Save] [Delete]  │ │
 *  │  │  [Force Equip □]      │   └──────────────────┘ │
 *  │  └──────────────────────┘                         │
 *  └──────────────────────────────────────────────────┘
 */
public class WardrobeScreen extends Screen {

    private static final int WIDTH  = 340;
    private static final int HEIGHT = 220;

    private final List<CosmeticSlot> SLOT_TABS = Arrays.asList(CosmeticSlot.values());

    private int selectedTabIndex = 0;
    private int scrollOffset = 0;
    private int selectedCosmeticIndex = -1;

    private int guiLeft, guiTop;

    // Preset panel state
    private int selectedPresetIndex = -1;
    private EditBox presetNameBox;

    public WardrobeScreen() {
        super(Component.translatable("gui.manas_cosmetics.wardrobe"));
    }

    @Override
    protected void init() {
        guiLeft = (width - WIDTH) / 2;
        guiTop  = (height - HEIGHT) / 2;

        // Slot tab buttons (top row)
        int tabX = guiLeft + 2;
        for (int i = 0; i < SLOT_TABS.size(); i++) {
            final int idx = i;
            CosmeticSlot slot = SLOT_TABS.get(i);
            addRenderableWidget(Button.builder(
                Component.literal(slotLabel(slot)),
                btn -> selectTab(idx)
            ).bounds(tabX + i * 22, guiTop + 2, 20, 12).build());
        }

        // Equip / Unequip buttons
        addRenderableWidget(Button.builder(
            Component.translatable("gui.manas_cosmetics.equip"),
            btn -> equipSelected()
        ).bounds(guiLeft + 4, guiTop + HEIGHT - 36, 60, 12).build());

        addRenderableWidget(Button.builder(
            Component.translatable("gui.manas_cosmetics.unequip"),
            btn -> unequipCurrent()
        ).bounds(guiLeft + 68, guiTop + HEIGHT - 36, 60, 12).build());

        // Force Equip toggle (only visible for weapon-type slots)
        addRenderableWidget(Button.builder(
            Component.translatable("gui.manas_cosmetics.force_equip"),
            btn -> toggleForceEquip()
        ).bounds(guiLeft + 4, guiTop + HEIGHT - 20, 80, 12).build());

        // Preset panel buttons
        addRenderableWidget(Button.builder(
            Component.translatable("gui.manas_cosmetics.save_preset"),
            btn -> savePreset()
        ).bounds(guiLeft + WIDTH - 90, guiTop + HEIGHT - 36, 40, 12).build());

        addRenderableWidget(Button.builder(
            Component.translatable("gui.manas_cosmetics.load_preset"),
            btn -> loadPreset()
        ).bounds(guiLeft + WIDTH - 46, guiTop + HEIGHT - 36, 40, 12).build());

        addRenderableWidget(Button.builder(
            Component.translatable("gui.manas_cosmetics.delete_preset"),
            btn -> deletePreset()
        ).bounds(guiLeft + WIDTH - 90, guiTop + HEIGHT - 20, 84, 12).build());

        // Preset name input box
        presetNameBox = new EditBox(
            font,
            guiLeft + WIDTH - 90, guiTop + HEIGHT - 52,
            84, 12,
            Component.translatable("gui.manas_cosmetics.preset_name")
        );
        presetNameBox.setMaxLength(24);
        presetNameBox.setValue("Preset " + (ClientCosmeticState.get().getPresets().size() + 1));
        addRenderableWidget(presetNameBox);
    }

    private void selectTab(int idx) {
        selectedTabIndex = idx;
        scrollOffset = 0;
        selectedCosmeticIndex = -1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g, mouseX, mouseY, delta);

        // Main panel background
        g.fill(guiLeft, guiTop, guiLeft + WIDTH, guiTop + HEIGHT, 0xCC1A1A1A);
        g.renderOutline(guiLeft, guiTop, WIDTH, HEIGHT, 0xFF555555);

        // Title
        g.drawString(font, title, guiLeft + 4, guiTop - 10, 0xFFFFFF);

        // Active slot label
        CosmeticSlot activeSlot = SLOT_TABS.get(selectedTabIndex);
        g.drawString(font, "Slot: " + activeSlot.getId(), guiLeft + 4, guiTop + 18, 0xAAAAAA);

        // Cosmetic list panel
        int listX = guiLeft + 4;
        int listY = guiTop + 30;
        int listW = WIDTH - 100;
        int listH = HEIGHT - 70;
        g.fill(listX, listY, listX + listW, listY + listH, 0xFF111111);

        List<CosmeticDefinition> cosmetics = getCosmeticsForSlot(activeSlot);
        for (int i = scrollOffset; i < cosmetics.size() && (i - scrollOffset) < 10; i++) {
            CosmeticDefinition def = cosmetics.get(i);
            int rowY = listY + 2 + (i - scrollOffset) * 14;
            boolean equipped = ClientCosmeticState.get().isEquipped(activeSlot, def.id());
            boolean selected = i == selectedCosmeticIndex;

            if (selected) g.fill(listX + 1, rowY, listX + listW - 1, rowY + 13, 0xFF2A4A7A);
            else if (mouseX >= listX && mouseX < listX + listW && mouseY >= rowY && mouseY < rowY + 13)
                g.fill(listX + 1, rowY, listX + listW - 1, rowY + 13, 0xFF252525);

            String prefix = equipped ? "✔ " : "  ";
            g.drawString(font, prefix + def.displayName(), listX + 4, rowY + 2, equipped ? 0x55FF55 : 0xDDDDDD);
        }

        // Preset panel
        int presetX = guiLeft + WIDTH - 92;
        int presetY = guiTop + 30;
        g.fill(presetX, presetY, presetX + 88, presetY + listH - 30, 0xFF111111);
        g.drawString(font, "Presets", presetX + 2, presetY - 9, 0xAAAAAA);

        List<io.github.manasmods.manas_cosmetics.data.CosmeticPreset> presets = ClientCosmeticState.get().getPresets();
        for (int i = 0; i < presets.size() && i < 8; i++) {
            int rowY = presetY + 2 + i * 14;
            boolean selected = i == selectedPresetIndex;
            if (selected) g.fill(presetX + 1, rowY, presetX + 87, rowY + 13, 0xFF2A4A7A);
            g.drawString(font, presets.get(i).getName(), presetX + 4, rowY + 2, 0xDDDDDD);
        }

        super.render(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Detect clicks in cosmetic list
        CosmeticSlot activeSlot = SLOT_TABS.get(selectedTabIndex);
        List<CosmeticDefinition> cosmetics = getCosmeticsForSlot(activeSlot);
        int listX = guiLeft + 4;
        int listY = guiTop + 30;
        int listW = WIDTH - 100;

        if (mouseX >= listX && mouseX < listX + listW) {
            int row = ((int) mouseY - listY - 2) / 14 + scrollOffset;
            if (row >= 0 && row < cosmetics.size()) {
                selectedCosmeticIndex = row;
            }
        }

        // Detect clicks in preset list
        int presetX = guiLeft + WIDTH - 92;
        int presetY = guiTop + 30;
        if (mouseX >= presetX && mouseX < presetX + 88) {
            int row = ((int) mouseY - presetY - 2) / 14;
            if (row >= 0 && row < ClientCosmeticState.get().getPresets().size()) {
                selectedPresetIndex = row;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = Math.max(0, scrollOffset - (int) scrollY);
        return true;
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    private void equipSelected() {
        if (selectedCosmeticIndex < 0) return;
        CosmeticSlot slot = SLOT_TABS.get(selectedTabIndex);
        List<CosmeticDefinition> cosmetics = getCosmeticsForSlot(slot);
        if (selectedCosmeticIndex >= cosmetics.size()) return;
        String id = cosmetics.get(selectedCosmeticIndex).id();
        ClientCosmeticState.get().equip(slot, id);
        sendEquipPacket(slot, id, false);
    }

    private void unequipCurrent() {
        CosmeticSlot slot = SLOT_TABS.get(selectedTabIndex);
        ClientCosmeticState.get().unequip(slot);
        sendUnequipPacket(slot);
    }

    private void toggleForceEquip() {
        CosmeticSlot slot = SLOT_TABS.get(selectedTabIndex);
        if (!slot.isWeaponSlot()) return;
        boolean current = ClientCosmeticState.get().isForceEquip(slot);
        ClientCosmeticState.get().setForceEquip(slot, !current);
        sendForceEquipPacket(slot, !current);
    }

    private void savePreset() {
        String name = presetNameBox.getValue().trim();
        if (name.isEmpty()) return;
        ClientCosmeticState.get().savePreset(name);
        sendPresetSavePacket(name);
    }

    private void loadPreset() {
        if (selectedPresetIndex < 0) return;
        ClientCosmeticState.get().loadPreset(selectedPresetIndex);
        sendPresetLoadPacket(selectedPresetIndex);
    }

    private void deletePreset() {
        if (selectedPresetIndex < 0) return;
        ClientCosmeticState.get().deletePreset(selectedPresetIndex);
        sendPresetDeletePacket(selectedPresetIndex);
        selectedPresetIndex = -1;
    }

    // ── Network helpers ────────────────────────────────────────────────────────

    /** Creates a {@link RegistryFriendlyByteBuf} backed by the current connection's registry access. */
    private static RegistryFriendlyByteBuf createBuf() {
        Minecraft mc = Minecraft.getInstance();
        RegistryAccess access = mc.getConnection() != null
            ? mc.getConnection().registryAccess()
            : RegistryAccess.EMPTY;
        return new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), access);
    }

    private void sendEquipPacket(CosmeticSlot slot, String id, boolean forceEquip) {
        RegistryFriendlyByteBuf buf = createBuf();
        buf.writeUtf(slot.getId());
        buf.writeUtf(id);
        buf.writeBoolean(forceEquip);
        dev.architectury.networking.NetworkManager.sendToServer(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("manas_cosmetics", "equip_c2s"), buf);
    }

    private void sendUnequipPacket(CosmeticSlot slot) {
        RegistryFriendlyByteBuf buf = createBuf();
        buf.writeUtf(slot.getId());
        dev.architectury.networking.NetworkManager.sendToServer(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("manas_cosmetics", "unequip_c2s"), buf);
    }

    private void sendForceEquipPacket(CosmeticSlot slot, boolean value) {
        RegistryFriendlyByteBuf buf = createBuf();
        buf.writeUtf(slot.getId());
        buf.writeBoolean(value);
        dev.architectury.networking.NetworkManager.sendToServer(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("manas_cosmetics", "force_equip_c2s"), buf);
    }

    private void sendPresetSavePacket(String name) {
        RegistryFriendlyByteBuf buf = createBuf();
        buf.writeUtf(name);
        dev.architectury.networking.NetworkManager.sendToServer(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("manas_cosmetics", "preset_save_c2s"), buf);
    }

    private void sendPresetLoadPacket(int index) {
        RegistryFriendlyByteBuf buf = createBuf();
        buf.writeVarInt(index);
        dev.architectury.networking.NetworkManager.sendToServer(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("manas_cosmetics", "preset_load_c2s"), buf);
    }

    private void sendPresetDeletePacket(int index) {
        RegistryFriendlyByteBuf buf = createBuf();
        buf.writeVarInt(index);
        dev.architectury.networking.NetworkManager.sendToServer(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("manas_cosmetics", "preset_delete_c2s"), buf);
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    private List<CosmeticDefinition> getCosmeticsForSlot(CosmeticSlot slot) {
        List<CosmeticDefinition> result = new ArrayList<>();
        for (CosmeticDefinition def : ClientCosmeticState.get().getAvailableCosmetics()) {
            if (def.slot() == slot) result.add(def);
        }
        return result;
    }

    private String slotLabel(CosmeticSlot slot) {
        return slot.getId().substring(0, Math.min(3, slot.getId().length())).toUpperCase();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
