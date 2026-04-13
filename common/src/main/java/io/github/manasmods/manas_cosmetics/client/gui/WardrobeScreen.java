package io.github.manasmods.manas_cosmetics.client.gui;

import io.github.manasmods.manas_cosmetics.api.CosmeticDefinition;
import io.github.manasmods.manas_cosmetics.api.CosmeticSlot;
import io.github.manasmods.manas_cosmetics.client.ClientCosmeticState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Wardrobe GUI — Left-Alt or /manas_cosmetics wardrobe.
 *
 * Layout:
 *  ┌─[Title bar]────────────────────────────────────────────────┐
 *  │ Slot     │  Cosmetic list              │ [Player preview]  │
 *  │ sidebar  │                             │                   │
 *  │ (scroll) │                             ├───────────────────┤
 *  │          │                             │ Preset list       │
 *  ├──────────┼─────────────────────────────┼───────────────────┤
 *  │ [Equip]  [Unequip]                     │ [Name] [Sv][Ld]   │
 *  │ [Force Equip]                          │ [Delete]          │
 *  └────────────────────────────────────────────────────────────┘
 */
public class WardrobeScreen extends Screen {

    // ── Layout ─────────────────────────────────────────────────────────────────

    private static final int WIDTH        = 400;
    private static final int HEIGHT       = 270;
    private static final int HEADER_H     = 14;   // title bar height
    private static final int BOTTOM_H     = 64;   // action buttons height
    private static final int CONTENT_H    = HEIGHT - HEADER_H - BOTTOM_H; // 192
    private static final int SIDEBAR_W    = 72;   // slot sidebar width
    private static final int RIGHT_W      = 130;  // right panel width
    // cosmetic list fills the middle
    private static final int LIST_W       = WIDTH - SIDEBAR_W - RIGHT_W - 4; // ~194
    // right panel split: preview top half, presets bottom half
    private static final int PREVIEW_H    = CONTENT_H / 2;   // 96
    private static final int PRESET_LIST_H = CONTENT_H - PREVIEW_H; // 96

    private static final int ROW_H = 13;

    // ── Palette (brighter / higher contrast) ──────────────────────────────────

    private static final int COL_OVERLAY   = 0xA0000000;  // world overlay
    private static final int COL_PANEL     = 0xFF1E2E40;  // main panel bg
    private static final int COL_INNER     = 0xFF111E2C;  // inner areas
    private static final int COL_HEADER    = 0xFF182437;  // title bar
    private static final int COL_BORDER_DK = 0xFF080F18;  // shadow border
    private static final int COL_BORDER_LT = 0xFF4D9FE0;  // highlight border (bright)
    private static final int COL_DIVIDER   = 0xFF2A4A6A;  // inner dividers
    private static final int COL_ACCENT    = 0xFF55AAFF;  // active-tab top strip
    private static final int COL_SEL       = 0xFF2255AA;  // selected row/tab
    private static final int COL_HOV       = 0xFF1A3A55;  // hovered row
    private static final int COL_TEXT      = 0xFFFFFFFF;  // primary text
    private static final int COL_TEXT_DIM  = 0xFF8BBEDD;  // secondary text
    private static final int COL_TITLE     = 0xFF77CCFF;  // title
    private static final int COL_EQUIPPED  = 0xFF44FF88;  // equipped tick

    // ── Persistent GUI state (survives between openings in the same session) ───

    private static int savedTabIndex      = 0;
    private static int savedSlotScroll    = 0;
    private static int savedListScroll    = 0;
    private static int savedCosmeticIndex = -1;
    private static int savedPresetIndex   = -1;

    /** Called on client disconnect so stale state is not restored next session. */
    public static void clearSavedState() {
        savedTabIndex      = 0;
        savedSlotScroll    = 0;
        savedListScroll    = 0;
        savedCosmeticIndex = -1;
        savedPresetIndex   = -1;
    }

    // ── Instance state ─────────────────────────────────────────────────────────

    private final List<CosmeticSlot> SLOT_TABS = Arrays.asList(CosmeticSlot.values());

    private int selectedTabIndex      = savedTabIndex;
    private int slotScrollOffset      = savedSlotScroll;
    private int listScrollOffset      = savedListScroll;
    private int selectedCosmeticIndex = savedCosmeticIndex;
    private int selectedPresetIndex   = savedPresetIndex;

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

        int bY1 = guiTop + HEADER_H + CONTENT_H + 4;   // button row 1
        int bY2 = bY1 + 24;                             // button row 2

        // Left / main area buttons
        addRenderableWidget(Button.builder(
                Component.translatable("gui.manas_cosmetics.equip"),
                btn -> equipSelected()
        ).bounds(guiLeft + SIDEBAR_W + 4, bY1, 56, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.manas_cosmetics.unequip"),
                btn -> unequipCurrent()
        ).bounds(guiLeft + SIDEBAR_W + 64, bY1, 66, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.manas_cosmetics.force_equip"),
                btn -> toggleForceEquip()
        ).bounds(guiLeft + SIDEBAR_W + 4, bY2, 90, 20).build());

        // Right panel buttons
        int rx = guiLeft + WIDTH - RIGHT_W + 2;
        int rbW = RIGHT_W - 6;

        presetNameBox = new EditBox(
                font, rx, bY1, rbW, 16,
                Component.translatable("gui.manas_cosmetics.preset_name")
        );
        presetNameBox.setMaxLength(24);
        presetNameBox.setValue("Preset " + (ClientCosmeticState.get().getPresets().size() + 1));
        addRenderableWidget(presetNameBox);

        int halfW = (rbW - 2) / 2;
        addRenderableWidget(Button.builder(
                Component.translatable("gui.manas_cosmetics.save_preset"),
                btn -> savePreset()
        ).bounds(rx, bY2, halfW, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.manas_cosmetics.load_preset"),
                btn -> loadPreset()
        ).bounds(rx + halfW + 2, bY2, halfW, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.manas_cosmetics.delete_preset"),
                btn -> deletePreset()
        ).bounds(rx, bY2 + 24, rbW, 20).build());
    }

    // ── Background ─────────────────────────────────────────────────────────────

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, COL_OVERLAY);
    }

    // ── Render ─────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g, mouseX, mouseY, delta);

        final int cx = guiLeft;
        final int cy = guiTop;
        final int contentY = cy + HEADER_H;

        // ── Outer panel ─────────────────────────────────────────────────────
        drawPanel(g, cx, cy, WIDTH, HEIGHT);

        // Title bar
        g.fill(cx + 1, cy + 1, cx + WIDTH - 1, cy + HEADER_H, COL_HEADER);
        g.drawString(font, title, cx + 5, cy + 3, COL_TITLE, false);

        // ── Slot sidebar ─────────────────────────────────────────────────────
        int sideX  = cx + 1;
        int sideY  = contentY;
        int sideH  = CONTENT_H;
        g.fill(sideX, sideY, sideX + SIDEBAR_W, sideY + sideH, COL_INNER);

        int visibleSlots = sideH / ROW_H;
        g.enableScissor(sideX, sideY, sideX + SIDEBAR_W, sideY + sideH);
        for (int i = slotScrollOffset; i < SLOT_TABS.size() && (i - slotScrollOffset) < visibleSlots; i++) {
            CosmeticSlot slot = SLOT_TABS.get(i);
            int rowY  = sideY + (i - slotScrollOffset) * ROW_H;
            boolean active   = (i == selectedTabIndex);
            boolean anyEquip = ClientCosmeticState.get().getEquipped(slot).isPresent();

            if (active) {
                g.fill(sideX, rowY, sideX + SIDEBAR_W, rowY + ROW_H, COL_SEL);
                g.fill(sideX, rowY, sideX + 2, rowY + ROW_H, COL_ACCENT); // left accent bar
            } else if (mouseX >= sideX && mouseX < sideX + SIDEBAR_W
                    && mouseY >= rowY && mouseY < rowY + ROW_H) {
                g.fill(sideX, rowY, sideX + SIDEBAR_W, rowY + ROW_H, COL_HOV);
            }

            String dot  = anyEquip ? "\u25CF " : "  ";
            int txtCol  = active ? COL_TEXT : COL_TEXT_DIM;
            g.drawString(font, dot + slotLabel(slot), sideX + 5, rowY + 2, anyEquip ? COL_EQUIPPED : txtCol, false);
        }
        g.disableScissor();

        // Sidebar / list divider
        int divX = cx + SIDEBAR_W + 1;
        g.fill(divX, contentY, divX + 1, contentY + CONTENT_H, COL_DIVIDER);

        // ── Cosmetic list ────────────────────────────────────────────────────
        CosmeticSlot activeSlot = SLOT_TABS.get(selectedTabIndex);
        int listX = divX + 2;
        int listY = contentY;
        int listH = CONTENT_H;
        g.fill(listX, listY, listX + LIST_W, listY + listH, COL_INNER);

        // Slot header
        g.drawString(font,
                activeSlot.getId().replace('_', ' ').toUpperCase(),
                listX + 4, listY + 2, COL_TEXT_DIM, false);

        int itemY0 = listY + ROW_H + 2;
        int itemH  = listH - ROW_H - 2;
        g.enableScissor(listX, itemY0, listX + LIST_W, itemY0 + itemH);
        List<CosmeticDefinition> cosmetics = getCosmeticsForSlot(activeSlot);
        int visRows = itemH / ROW_H;
        for (int i = listScrollOffset; i < cosmetics.size() && (i - listScrollOffset) < visRows; i++) {
            CosmeticDefinition def = cosmetics.get(i);
            int rowY  = itemY0 + (i - listScrollOffset) * ROW_H;
            boolean eq  = ClientCosmeticState.get().isEquipped(activeSlot, def.id());
            boolean sel = (i == selectedCosmeticIndex);

            if (sel) {
                g.fill(listX, rowY, listX + LIST_W, rowY + ROW_H, COL_SEL);
            } else if (mouseX >= listX && mouseX < listX + LIST_W
                    && mouseY >= rowY && mouseY < rowY + ROW_H) {
                g.fill(listX, rowY, listX + LIST_W, rowY + ROW_H, COL_HOV);
            }
            g.drawString(font,
                    (eq ? "\u2714 " : "  ") + def.displayName(),
                    listX + 4, rowY + 2,
                    eq ? COL_EQUIPPED : COL_TEXT, false);
        }
        g.disableScissor();

        // List / right-panel divider
        int rdivX = listX + LIST_W + 1;
        g.fill(rdivX, contentY, rdivX + 1, contentY + CONTENT_H, COL_DIVIDER);

        // ── Right panel ──────────────────────────────────────────────────────
        int rpX = rdivX + 2;
        int rpW = cx + WIDTH - 1 - rpX;

        // ── Player preview (top half) ────────────────────────────────────────
        int prevY = contentY;
        g.fill(rpX, prevY, rpX + rpW, prevY + PREVIEW_H, COL_INNER);
        g.drawString(font, "Preview", rpX + 3, prevY + 2, COL_TEXT_DIM, false);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            int scale = 38;
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    g,
                    rpX + 1, prevY + ROW_H,
                    rpX + rpW - 1, prevY + PREVIEW_H - 1,
                    scale, 0f,
                    mouseX, mouseY,
                    mc.player
            );
        }

        // Separator between preview and presets
        int sepY = contentY + PREVIEW_H;
        g.fill(rpX, sepY, rpX + rpW, sepY + 1, COL_DIVIDER);

        // ── Preset list (bottom half) ─────────────────────────────────────────
        int plY = sepY + 1;
        int plH = PRESET_LIST_H - 1;
        g.fill(rpX, plY, rpX + rpW, plY + plH, COL_INNER);
        g.drawString(font, "Presets", rpX + 3, plY + 2, COL_TEXT_DIM, false);

        int plItemY0 = plY + ROW_H + 2;
        int plItemH  = plH - ROW_H - 2;
        g.enableScissor(rpX, plItemY0, rpX + rpW, plItemY0 + plItemH);
        List<io.github.manasmods.manas_cosmetics.data.CosmeticPreset> presets =
                ClientCosmeticState.get().getPresets();
        int plVisRows = plItemH / ROW_H;
        for (int i = 0; i < presets.size() && i < plVisRows; i++) {
            int rowY = plItemY0 + i * ROW_H;
            if (i == selectedPresetIndex) {
                g.fill(rpX, rowY, rpX + rpW, rowY + ROW_H, COL_SEL);
            } else if (mouseX >= rpX && mouseX < rpX + rpW
                    && mouseY >= rowY && mouseY < rowY + ROW_H) {
                g.fill(rpX, rowY, rpX + rpW, rowY + ROW_H, COL_HOV);
            }
            g.drawString(font, presets.get(i).getName(), rpX + 4, rowY + 2, COL_TEXT, false);
        }
        g.disableScissor();

        super.render(g, mouseX, mouseY, delta);
    }

    // ── Input ──────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        final int contentY = guiTop + HEADER_H;

        // Slot sidebar clicks
        int sideX = guiLeft + 1;
        if (mouseX >= sideX && mouseX < sideX + SIDEBAR_W
                && mouseY >= contentY && mouseY < contentY + CONTENT_H) {
            int row = ((int) mouseY - contentY) / ROW_H + slotScrollOffset;
            if (row >= 0 && row < SLOT_TABS.size()) {
                selectTab(row);
                return true;
            }
        }

        // Cosmetic list clicks
        CosmeticSlot activeSlot = SLOT_TABS.get(selectedTabIndex);
        List<CosmeticDefinition> cosmetics = getCosmeticsForSlot(activeSlot);
        int divX  = guiLeft + SIDEBAR_W + 1;
        int listX = divX + 2;
        int itemY0 = contentY + ROW_H + 2;
        if (mouseX >= listX && mouseX < listX + LIST_W
                && mouseY >= itemY0 && mouseY < itemY0 + CONTENT_H - ROW_H - 2) {
            int row = ((int) mouseY - itemY0) / ROW_H + listScrollOffset;
            if (row >= 0 && row < cosmetics.size()) {
                selectedCosmeticIndex = row;
            }
        }

        // Preset list clicks
        int rdivX = listX + LIST_W + 1;
        int rpX   = rdivX + 2;
        int sepY  = contentY + PREVIEW_H;
        int plItemY0 = sepY + 1 + ROW_H + 2;
        int plH   = PRESET_LIST_H - 1;
        int plItemH = plH - ROW_H - 2;
        if (mouseX >= rpX && mouseX < guiLeft + WIDTH - 1
                && mouseY >= plItemY0 && mouseY < plItemY0 + plItemH) {
            int row = ((int) mouseY - plItemY0) / ROW_H;
            if (row >= 0 && row < ClientCosmeticState.get().getPresets().size()) {
                selectedPresetIndex = row;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        final int contentY = guiTop + HEADER_H;
        int sideX = guiLeft + 1;
        int divX  = guiLeft + SIDEBAR_W + 1;
        int listX = divX + 2;

        if (mouseX >= sideX && mouseX < sideX + SIDEBAR_W
                && mouseY >= contentY && mouseY < contentY + CONTENT_H) {
            slotScrollOffset = Math.max(0, Math.min(
                    SLOT_TABS.size() - CONTENT_H / ROW_H,
                    slotScrollOffset - (int) scrollY));
        } else if (mouseX >= listX && mouseX < listX + LIST_W
                && mouseY >= contentY && mouseY < contentY + CONTENT_H) {
            listScrollOffset = Math.max(0, listScrollOffset - (int) scrollY);
        }
        return true;
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    private void selectTab(int idx) {
        selectedTabIndex      = idx;
        listScrollOffset      = 0;
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

    private void sendEquipPacket(CosmeticSlot slot, String id, boolean forceEquip) {
        dev.architectury.networking.NetworkManager.sendToServer(
            new io.github.manasmods.manas_cosmetics.network.WardrobePayloads.EquipPayload(slot.getId(), id, forceEquip));
    }

    private void sendUnequipPacket(CosmeticSlot slot) {
        dev.architectury.networking.NetworkManager.sendToServer(
                new io.github.manasmods.manas_cosmetics.network.WardrobePayloads.UnequipPayload(slot.getId()));
    }

    private void sendForceEquipPacket(CosmeticSlot slot, boolean value) {
        dev.architectury.networking.NetworkManager.sendToServer(
                new io.github.manasmods.manas_cosmetics.network.WardrobePayloads.ForceEquipPayload(slot.getId(), value));
    }

    private void sendPresetSavePacket(String name) {
        dev.architectury.networking.NetworkManager.sendToServer(
                new io.github.manasmods.manas_cosmetics.network.WardrobePayloads.PresetSavePayload(name));
    }

    private void sendPresetLoadPacket(int index) {
        dev.architectury.networking.NetworkManager.sendToServer(
                new io.github.manasmods.manas_cosmetics.network.WardrobePayloads.PresetLoadPayload(index));
    }

    private void sendPresetDeletePacket(int index) {
        dev.architectury.networking.NetworkManager.sendToServer(
                new io.github.manasmods.manas_cosmetics.network.WardrobePayloads.PresetDeletePayload(index));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Two-layer panel: outer dark shadow → inner bright-blue border → content fill.
     */
    private static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, COL_BORDER_DK);
        g.fill(x,     y,     x + w,     y + h,     COL_BORDER_LT);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, COL_PANEL);
    }

    private List<CosmeticDefinition> getCosmeticsForSlot(CosmeticSlot slot) {
        List<CosmeticDefinition> result = new ArrayList<>();
        for (CosmeticDefinition def : ClientCosmeticState.get().getAvailableCosmetics()) {
            if (def.slot() == slot) result.add(def);
        }
        return result;
    }

    private String slotLabel(CosmeticSlot slot) {
        return switch (slot) {
            case HELMET      -> "Helmet";
            case ABOVE_HEAD  -> "Above Head";
            case CHESTPLATE  -> "Chestplate";
            case BACK        -> "Back";
            case FRONT       -> "Front";
            case LEGS        -> "Legs";
            case BOOTS       -> "Boots";
            case EARS        -> "Ears";
            case ORBIT       -> "Orbit";
            case PET         -> "Pet";
            case WEAPON      -> "Weapon";
            case SHIELD      -> "Shield";
            case GRIMOIRE    -> "Grimoire";
            case MAGIC_STAFF -> "Magic Staff";
        };
    }

    @Override
    public void removed() {
        // Persist state for next opening
        savedTabIndex      = selectedTabIndex;
        savedSlotScroll    = slotScrollOffset;
        savedListScroll    = listScrollOffset;
        savedCosmeticIndex = selectedCosmeticIndex;
        savedPresetIndex   = selectedPresetIndex;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}