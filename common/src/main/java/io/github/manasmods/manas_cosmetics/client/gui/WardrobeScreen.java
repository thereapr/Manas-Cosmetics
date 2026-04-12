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

/**
 * The wardrobe GUI — opened with Left-Alt or /manas_cosmetics wardrobe.
 *
 * Bluish pixel-art Minecraft style. Slot tabs are drawn as custom boxes
 * (not Button widgets) so they can be coloured/highlighted properly.
 */
public class WardrobeScreen extends Screen {

    // ── Layout ─────────────────────────────────────────────────────────────────

    private static final int WIDTH  = 340;
    private static final int HEIGHT = 230;

    /** Width of each slot-tab cell. 13 slots × 24 px = 312 px — fits in 340. */
    private static final int TAB_W = 24;
    private static final int TAB_H = 13;

    // ── Bluish pixel-art palette ───────────────────────────────────────────────

    /** World overlay — replaces MC's blur shader with a plain dark wash. */
    private static final int COL_OVERLAY   = 0x88000000;
    /** Main panel background. */
    private static final int COL_PANEL     = 0xF0101828;
    /** List / inner-panel background. */
    private static final int COL_INNER     = 0xF0070D18;
    /** Dark border (outer / shadow edge). */
    private static final int COL_BORDER_DK = 0xFF04080F;
    /** Light border (inner / highlight edge). */
    private static final int COL_BORDER_LT = 0xFF2A5898;
    /** Bright accent used for the active-tab top strip. */
    private static final int COL_ACCENT    = 0xFF3A78CC;
    /** Selected-row / active-tab fill. */
    private static final int COL_SEL       = 0xFF1A4A88;
    /** Hovered-row fill. */
    private static final int COL_HOV       = 0xFF0E1E30;
    /** Primary text — light blue-white. */
    private static final int COL_TEXT      = 0xFFCCE4FF;
    /** Dimmed text — slate blue. */
    private static final int COL_TEXT_DIM  = 0xFF5577AA;
    /** Title text — brighter blue. */
    private static final int COL_TITLE     = 0xFF88CCFF;
    /** Equipped-item indicator. */
    private static final int COL_EQUIPPED  = 0xFF55FF88;

    // ── State ──────────────────────────────────────────────────────────────────

    private final List<CosmeticSlot> SLOT_TABS = Arrays.asList(CosmeticSlot.values());

    private int selectedTabIndex      = 0;
    private int scrollOffset          = 0;
    private int selectedCosmeticIndex = -1;
    private int selectedPresetIndex   = -1;

    private int guiLeft, guiTop;
    private EditBox presetNameBox;

    // ── Constructor ────────────────────────────────────────────────────────────

    public WardrobeScreen() {
        super(Component.translatable("gui.manas_cosmetics.wardrobe"));
    }

    // ── Init ───────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        guiLeft = (width  - WIDTH)  / 2;
        guiTop  = (height - HEIGHT) / 2;

        // NOTE: slot tabs are drawn + clicked manually — no Button widgets for them.

        // Equip / Unequip — wide enough that the text is never clipped
        addRenderableWidget(Button.builder(
            Component.translatable("gui.manas_cosmetics.equip"),
            btn -> equipSelected()
        ).bounds(guiLeft + 4, guiTop + HEIGHT - 44, 56, 20).build());

        addRenderableWidget(Button.builder(
            Component.translatable("gui.manas_cosmetics.unequip"),
            btn -> unequipCurrent()
        ).bounds(guiLeft + 64, guiTop + HEIGHT - 44, 66, 20).build());

        // Force-equip toggle (weapon slots only — greyed out otherwise)
        addRenderableWidget(Button.builder(
            Component.translatable("gui.manas_cosmetics.force_equip"),
            btn -> toggleForceEquip()
        ).bounds(guiLeft + 4, guiTop + HEIGHT - 20, 90, 20).build());

        // Preset name input box
        presetNameBox = new EditBox(
            font,
            guiLeft + WIDTH - 92, guiTop + HEIGHT - 70,
            86, 16,
            Component.translatable("gui.manas_cosmetics.preset_name")
        );
        presetNameBox.setMaxLength(24);
        presetNameBox.setValue("Preset " + (ClientCosmeticState.get().getPresets().size() + 1));
        addRenderableWidget(presetNameBox);

        // Save / Load / Delete
        addRenderableWidget(Button.builder(
            Component.translatable("gui.manas_cosmetics.save_preset"),
            btn -> savePreset()
        ).bounds(guiLeft + WIDTH - 92, guiTop + HEIGHT - 50, 40, 20).build());

        addRenderableWidget(Button.builder(
            Component.translatable("gui.manas_cosmetics.load_preset"),
            btn -> loadPreset()
        ).bounds(guiLeft + WIDTH - 48, guiTop + HEIGHT - 50, 40, 20).build());

        addRenderableWidget(Button.builder(
            Component.translatable("gui.manas_cosmetics.delete_preset"),
            btn -> deletePreset()
        ).bounds(guiLeft + WIDTH - 92, guiTop + HEIGHT - 26, 84, 20).build());
    }

    // ── Background ─────────────────────────────────────────────────────────────

    /**
     * Replace MC's default blur pass with a plain semi-transparent overlay so
     * the GUI text and panels stay crisp.
     */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, COL_OVERLAY);
    }

    // ── Render ─────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g, mouseX, mouseY, delta);

        // Main panel
        drawPanel(g, guiLeft, guiTop, WIDTH, HEIGHT);

        // Title (inside panel, top-left)
        g.drawString(font, title, guiLeft + 6, guiTop + 4, COL_TITLE, false);

        // ── Slot tabs ───────────────────────────────────────────────────────
        int tabY      = guiTop + 18;
        int tabStartX = guiLeft + 2;
        for (int i = 0; i < SLOT_TABS.size(); i++) {
            int tx     = tabStartX + i * TAB_W;
            boolean active = (i == selectedTabIndex);

            if (active) {
                g.fill(tx,     tabY,     tx + TAB_W, tabY + 2,     COL_ACCENT); // top-accent strip
                g.fill(tx,     tabY + 2, tx + TAB_W, tabY + TAB_H, COL_SEL);
            } else {
                g.fill(tx,     tabY,     tx + TAB_W, tabY + TAB_H, COL_HOV);
            }
            // Thin side borders for each cell
            g.fill(tx,             tabY, tx + 1,         tabY + TAB_H, COL_BORDER_DK);
            g.fill(tx + TAB_W - 1, tabY, tx + TAB_W,     tabY + TAB_H, COL_BORDER_DK);

            g.drawCenteredString(font, slotLabel(SLOT_TABS.get(i)),
                tx + TAB_W / 2, tabY + 3,
                active ? COL_TEXT : COL_TEXT_DIM);
        }

        // Divider line below tab row
        g.fill(guiLeft + 1, tabY + TAB_H, guiLeft + WIDTH - 1, tabY + TAB_H + 1, COL_BORDER_LT);

        // Active-slot name
        CosmeticSlot activeSlot = SLOT_TABS.get(selectedTabIndex);
        g.drawString(font,
            "Slot: " + activeSlot.getId().replace('_', ' '),
            guiLeft + 6, tabY + TAB_H + 4,
            COL_TEXT_DIM, false);

        // ── Cosmetic list ───────────────────────────────────────────────────
        int listX = guiLeft + 4;
        int listY = tabY + TAB_H + 16;
        int listW = WIDTH - 102;
        int listH = HEIGHT - (listY - guiTop) - 52;
        int rowH  = 13;
        drawPanel(g, listX, listY, listW, listH);

        g.enableScissor(listX + 1, listY + 1, listX + listW - 1, listY + listH - 1);
        List<CosmeticDefinition> cosmetics = getCosmeticsForSlot(activeSlot);
        int visibleRows = (listH - 2) / rowH;
        for (int i = scrollOffset; i < cosmetics.size() && (i - scrollOffset) < visibleRows; i++) {
            CosmeticDefinition def  = cosmetics.get(i);
            int rowY    = listY + 1 + (i - scrollOffset) * rowH;
            boolean eq  = ClientCosmeticState.get().isEquipped(activeSlot, def.id());
            boolean sel = (i == selectedCosmeticIndex);

            if (sel) {
                g.fill(listX + 1, rowY, listX + listW - 1, rowY + rowH, COL_SEL);
            } else if (mouseX >= listX + 1 && mouseX < listX + listW - 1
                    && mouseY >= rowY && mouseY < rowY + rowH) {
                g.fill(listX + 1, rowY, listX + listW - 1, rowY + rowH, COL_HOV);
            }
            g.drawString(font,
                (eq ? "\u2714 " : "  ") + def.displayName(),
                listX + 5, rowY + 2,
                eq ? COL_EQUIPPED : COL_TEXT, false);
        }
        g.disableScissor();

        // ── Preset panel ────────────────────────────────────────────────────
        int presetX = guiLeft + WIDTH - 94;
        int presetY = listY;
        int presetW = 88;
        int presetH = listH;
        drawPanel(g, presetX, presetY, presetW, presetH);
        g.drawString(font, "Presets", presetX + 4, presetY - 9, COL_TEXT_DIM, false);

        g.enableScissor(presetX + 1, presetY + 1, presetX + presetW - 1, presetY + presetH - 1);
        List<io.github.manasmods.manas_cosmetics.data.CosmeticPreset> presets =
            ClientCosmeticState.get().getPresets();
        for (int i = 0; i < presets.size() && i < 8; i++) {
            int rowY = presetY + 1 + i * rowH;
            if (i == selectedPresetIndex) {
                g.fill(presetX + 1, rowY, presetX + presetW - 1, rowY + rowH, COL_SEL);
            } else if (mouseX >= presetX + 1 && mouseX < presetX + presetW - 1
                    && mouseY >= rowY && mouseY < rowY + rowH) {
                g.fill(presetX + 1, rowY, presetX + presetW - 1, rowY + rowH, COL_HOV);
            }
            g.drawString(font, presets.get(i).getName(), presetX + 5, rowY + 2, COL_TEXT, false);
        }
        g.disableScissor();

        super.render(g, mouseX, mouseY, delta);
    }

    // ── Input ──────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Slot tab clicks (custom drawn, not Button widgets)
        int tabY      = guiTop + 18;
        int tabStartX = guiLeft + 2;
        if (mouseY >= tabY && mouseY < tabY + TAB_H) {
            for (int i = 0; i < SLOT_TABS.size(); i++) {
                int tx = tabStartX + i * TAB_W;
                if (mouseX >= tx && mouseX < tx + TAB_W) {
                    selectTab(i);
                    return true;
                }
            }
        }

        // Cosmetic list clicks
        CosmeticSlot activeSlot = SLOT_TABS.get(selectedTabIndex);
        List<CosmeticDefinition> cosmetics = getCosmeticsForSlot(activeSlot);
        int listX = guiLeft + 4;
        int listY = tabY + TAB_H + 16;
        int listW = WIDTH - 102;
        int listH = HEIGHT - (listY - guiTop) - 52;
        int rowH  = 13;
        if (mouseX >= listX + 1 && mouseX < listX + listW - 1
                && mouseY >= listY + 1 && mouseY < listY + listH - 1) {
            int row = ((int) mouseY - (listY + 1)) / rowH + scrollOffset;
            if (row >= 0 && row < cosmetics.size()) {
                selectedCosmeticIndex = row;
            }
        }

        // Preset list clicks
        int presetX = guiLeft + WIDTH - 94;
        int presetY = listY;
        int presetW = 88;
        if (mouseX >= presetX + 1 && mouseX < presetX + presetW - 1
                && mouseY >= presetY + 1 && mouseY < presetY + listH - 1) {
            int row = ((int) mouseY - (presetY + 1)) / rowH;
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

    private void selectTab(int idx) {
        selectedTabIndex      = idx;
        scrollOffset          = 0;
        selectedCosmeticIndex = -1;
    }

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

    // ── Network ────────────────────────────────────────────────────────────────

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

    // ── Drawing helpers ────────────────────────────────────────────────────────

    /**
     * MC-style inset panel: outer dark shadow → inner blue highlight → inner fill.
     */
    private static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, COL_BORDER_DK); // shadow outline
        g.fill(x,     y,     x + w,     y + h,     COL_BORDER_LT); // blue highlight border
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, COL_INNER);    // content area
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    private List<CosmeticDefinition> getCosmeticsForSlot(CosmeticSlot slot) {
        List<CosmeticDefinition> result = new ArrayList<>();
        for (CosmeticDefinition def : ClientCosmeticState.get().getAvailableCosmetics()) {
            if (def.slot() == slot) result.add(def);
        }
        return result;
    }

    /** Short tab labels that fit in a 24-px-wide cell. */
    private String slotLabel(CosmeticSlot slot) {
        return switch (slot) {
            case HELMET      -> "Helm";
            case ABOVE_HEAD  -> "Top";
            case CHESTPLATE  -> "Chst";
            case BACK        -> "Back";
            case FRONT       -> "Frnt";
            case LEGS        -> "Legs";
            case BOOTS       -> "Boot";
            case ORBIT       -> "Orbt";
            case PET         -> "Pet";
            case WEAPON      -> "Wpn";
            case SHIELD      -> "Shld";
            case GRIMOIRE    -> "Grim";
            case MAGIC_STAFF -> "Mgc";
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
