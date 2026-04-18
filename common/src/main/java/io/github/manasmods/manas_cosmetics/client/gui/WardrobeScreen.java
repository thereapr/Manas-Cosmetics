package io.github.manasmods.manas_cosmetics.client.gui;

import io.github.manasmods.manas_cosmetics.api.CosmeticDefinition;
import io.github.manasmods.manas_cosmetics.api.CosmeticSlot;
import io.github.manasmods.manas_cosmetics.api.WeaponType;
import io.github.manasmods.manas_cosmetics.client.ClientCosmeticState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
 *
 * The sidebar shows non-weapon slots, then a "── Weapon ──" header, then
 * per-weapon-type entries (Sword, Bow, …) and weapon-slot entries (Shield,
 * Grimoire, Magic Staff) as sub-items.
 */
public class WardrobeScreen extends Screen {

    // ── Sidebar entry types ────────────────────────────────────────────────────

    /** Sealed hierarchy for the items shown in the left sidebar. */
    private sealed interface SidebarEntry
            permits SidebarEntry.SlotSE, SidebarEntry.WeaponTypeSE, SidebarEntry.WeaponHeaderSE {

        /** A regular cosmetic slot. */
        record SlotSE(CosmeticSlot slot) implements SidebarEntry {}

        /** A per-weapon-type sub-entry (maps to WEAPON slot cosmetics filtered by weapon type). */
        record WeaponTypeSE(WeaponType weaponType) implements SidebarEntry {}

        /** Non-selectable group header for the weapon section. */
        record WeaponHeaderSE() implements SidebarEntry {}
    }

    // ── Layout ─────────────────────────────────────────────────────────────────

    private static final int WIDTH        = 400;
    private static final int HEIGHT       = 270;
    private static final int HEADER_H     = 14;
    private static final int BOTTOM_H     = 64;
    private static final int CONTENT_H    = HEIGHT - HEADER_H - BOTTOM_H;
    private static final int SIDEBAR_W    = 72;
    private static final int RIGHT_W      = 130;
    private static final int LIST_W       = WIDTH - SIDEBAR_W - RIGHT_W - 4;
    private static final int PREVIEW_H    = CONTENT_H / 2;
    private static final int PRESET_LIST_H = CONTENT_H - PREVIEW_H;

    private static final int ROW_H = 13;

    // ── Palette ────────────────────────────────────────────────────────────────

    private static final int COL_OVERLAY   = 0xA0000000;
    private static final int COL_PANEL     = 0xFF1E2E40;
    private static final int COL_INNER     = 0xFF111E2C;
    private static final int COL_HEADER    = 0xFF182437;
    private static final int COL_BORDER_DK = 0xFF080F18;
    private static final int COL_BORDER_LT = 0xFF4D9FE0;
    private static final int COL_DIVIDER   = 0xFF2A4A6A;
    private static final int COL_ACCENT    = 0xFF55AAFF;
    private static final int COL_SEL       = 0xFF2255AA;
    private static final int COL_HOV       = 0xFF1A3A55;
    private static final int COL_TEXT      = 0xFFFFFFFF;
    private static final int COL_TEXT_DIM  = 0xFFBBDEF7;
    private static final int COL_TITLE     = 0xFFAAE4FF;
    private static final int COL_EQUIPPED  = 0xFF44FF88;
    private static final int COL_HEADER_ENTRY = 0xFF4D9FE0;

    // ── Sidebar content ────────────────────────────────────────────────────────

    // Weapon types that have their own CosmeticSlot sub-entry (excluded from WeaponTypeSE list)
    private static final Set<String> WEAPON_SLOT_IDS = Set.of("shield", "grimoire", "magic_staff");

    private static List<SidebarEntry> buildSidebarEntries() {
        List<SidebarEntry> list = new ArrayList<>();
        // Non-weapon CosmeticSlot entries (in enum order)
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            if (!slot.isWeaponSlot()) list.add(new SidebarEntry.SlotSE(slot));
        }
        // Weapon group header (non-selectable divider)
        list.add(new SidebarEntry.WeaponHeaderSE());
        // Per-weapon-type sub-entries (skip ANY and weapon types covered by their own slot)
        for (WeaponType wt : WeaponType.values()) {
            if (wt != WeaponType.ANY && !WEAPON_SLOT_IDS.contains(wt.getId())) {
                list.add(new SidebarEntry.WeaponTypeSE(wt));
            }
        }
        // Weapon-slot sub-entries
        list.add(new SidebarEntry.SlotSE(CosmeticSlot.SHIELD));
        list.add(new SidebarEntry.SlotSE(CosmeticSlot.GRIMOIRE));
        list.add(new SidebarEntry.SlotSE(CosmeticSlot.MAGIC_STAFF));
        return list;
    }

    // ── Persistent GUI state ───────────────────────────────────────────────────

    private static int savedTabIndex      = 0;
    private static int savedSlotScroll    = 0;
    private static int savedListScroll    = 0;
    private static int savedCosmeticIndex = -1;
    private static int savedPresetIndex   = -1;

    public static void clearSavedState() {
        savedTabIndex      = 0;
        savedSlotScroll    = 0;
        savedListScroll    = 0;
        savedCosmeticIndex = -1;
        savedPresetIndex   = -1;
    }

    // ── Instance state ─────────────────────────────────────────────────────────

    private final List<SidebarEntry> SLOT_TABS = buildSidebarEntries();

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

        int bY1 = guiTop + HEADER_H + CONTENT_H + 2;
        int bY2 = bY1 + 20;

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

        int rx  = guiLeft + WIDTH - RIGHT_W + 2;
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
        ).bounds(rx, bY2 + 20, rbW, 20).build());
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

        drawPanel(g, cx, cy, WIDTH, HEIGHT);

        g.fill(cx + 1, cy + 1, cx + WIDTH - 1, cy + HEADER_H, COL_HEADER);
        g.drawString(font, title, cx + 5, cy + 3, COL_TITLE, true);

        // ── Slot sidebar ─────────────────────────────────────────────────────
        int sideX = cx + 1;
        int sideY = contentY;
        int sideH = CONTENT_H;
        g.fill(sideX, sideY, sideX + SIDEBAR_W, sideY + sideH, COL_INNER);

        int visibleSlots = sideH / ROW_H;
        g.enableScissor(sideX, sideY, sideX + SIDEBAR_W, sideY + sideH);
        for (int i = slotScrollOffset; i < SLOT_TABS.size() && (i - slotScrollOffset) < visibleSlots; i++) {
            SidebarEntry entry = SLOT_TABS.get(i);
            int rowY = sideY + (i - slotScrollOffset) * ROW_H;

            if (entry instanceof SidebarEntry.WeaponHeaderSE) {
                // Non-selectable group header
                g.fill(sideX, rowY, sideX + SIDEBAR_W, rowY + ROW_H, COL_INNER);
                g.drawString(font, "Weapon", sideX + 5, rowY + 2, COL_HEADER_ENTRY, true);
                continue;
            }

            boolean active    = (i == selectedTabIndex);
            boolean anyEquip  = isEntryEquipped(entry);
            boolean isSubEntry = isWeaponSubEntry(entry);

            if (active) {
                g.fill(sideX, rowY, sideX + SIDEBAR_W, rowY + ROW_H, COL_SEL);
                g.fill(sideX, rowY, sideX + 2, rowY + ROW_H, COL_ACCENT);
            } else if (mouseX >= sideX && mouseX < sideX + SIDEBAR_W
                    && mouseY >= rowY && mouseY < rowY + ROW_H) {
                g.fill(sideX, rowY, sideX + SIDEBAR_W, rowY + ROW_H, COL_HOV);
            }

            int indent = isSubEntry ? 10 : 0;
            String dot   = anyEquip ? "\u25CF " : "  ";
            int txtCol   = active ? COL_TEXT : COL_TEXT_DIM;
            g.drawString(font, dot + entryLabel(entry), sideX + 5 + indent, rowY + 2,
                    anyEquip ? COL_EQUIPPED : txtCol, true);
        }
        g.disableScissor();

        int divX = cx + SIDEBAR_W + 1;
        g.fill(divX, contentY, divX + 1, contentY + CONTENT_H, COL_DIVIDER);

        // ── Cosmetic list ─────────────────────────────────────────────────────
        SidebarEntry activeEntry = SLOT_TABS.get(selectedTabIndex);
        int listX = divX + 2;
        int listY = contentY;
        int listH = CONTENT_H;
        g.fill(listX, listY, listX + LIST_W, listY + listH, COL_INNER);

        g.drawString(font, entryLabel(activeEntry).toUpperCase(), listX + 4, listY + 2, COL_TEXT_DIM, true);

        int itemY0 = listY + ROW_H + 2;
        int itemH  = listH - ROW_H - 2;
        g.enableScissor(listX, itemY0, listX + LIST_W, itemY0 + itemH);
        List<CosmeticDefinition> cosmetics = getCosmeticsForEntry(activeEntry);
        int visRows = itemH / ROW_H;
        for (int i = listScrollOffset; i < cosmetics.size() && (i - listScrollOffset) < visRows; i++) {
            CosmeticDefinition def = cosmetics.get(i);
            int rowY = itemY0 + (i - listScrollOffset) * ROW_H;
            boolean eq  = isEquippedInList(activeEntry, def);
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
                    eq ? COL_EQUIPPED : COL_TEXT, true);
        }
        g.disableScissor();

        int rdivX = listX + LIST_W + 1;
        g.fill(rdivX, contentY, rdivX + 1, contentY + CONTENT_H, COL_DIVIDER);

        // ── Right panel ───────────────────────────────────────────────────────
        int rpX = rdivX + 2;
        int rpW = cx + WIDTH - 1 - rpX;

        int prevY = contentY;
        g.fill(rpX, prevY, rpX + rpW, prevY + PREVIEW_H, COL_INNER);
        g.drawString(font, "Preview", rpX + 3, prevY + 2, COL_TEXT_DIM, true);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    g,
                    rpX + 1, prevY + ROW_H,
                    rpX + rpW - 1, prevY + PREVIEW_H - 1,
                    38, 0f,
                    mouseX, mouseY,
                    mc.player
            );
        }

        int sepY = contentY + PREVIEW_H;
        g.fill(rpX, sepY, rpX + rpW, sepY + 1, COL_DIVIDER);

        int plY  = sepY + 1;
        int plH  = PRESET_LIST_H - 1;
        g.fill(rpX, plY, rpX + rpW, plY + plH, COL_INNER);
        g.drawString(font, "Presets", rpX + 3, plY + 2, COL_TEXT_DIM, true);

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
            g.drawString(font, presets.get(i).getName(), rpX + 4, rowY + 2, COL_TEXT, true);
        }
        g.disableScissor();

        super.render(g, mouseX, mouseY, delta);
    }

    // ── Input ──────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        final int contentY = guiTop + HEADER_H;

        int sideX = guiLeft + 1;
        if (mouseX >= sideX && mouseX < sideX + SIDEBAR_W
                && mouseY >= contentY && mouseY < contentY + CONTENT_H) {
            int row = ((int) mouseY - contentY) / ROW_H + slotScrollOffset;
            if (row >= 0 && row < SLOT_TABS.size()) {
                // Skip non-selectable headers
                if (!(SLOT_TABS.get(row) instanceof SidebarEntry.WeaponHeaderSE)) {
                    selectTab(row);
                }
                return true;
            }
        }

        SidebarEntry activeEntry = SLOT_TABS.get(selectedTabIndex);
        List<CosmeticDefinition> cosmetics = getCosmeticsForEntry(activeEntry);
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
        SidebarEntry entry = SLOT_TABS.get(selectedTabIndex);
        List<CosmeticDefinition> cosmetics = getCosmeticsForEntry(entry);
        if (selectedCosmeticIndex >= cosmetics.size()) return;
        String id = cosmetics.get(selectedCosmeticIndex).id();
        switch (entry) {
            case SidebarEntry.SlotSE se -> {
                ClientCosmeticState.get().equip(se.slot(), id);
                sendEquipPacket(se.slot(), id, false);
            }
            case SidebarEntry.WeaponTypeSE wte -> {
                ClientCosmeticState.get().equipWeapon(wte.weaponType(), id);
                sendEquipWeaponPacket(wte.weaponType(), id, false);
            }
            case SidebarEntry.WeaponHeaderSE ignored -> {}
        }
    }

    private void unequipCurrent() {
        SidebarEntry entry = SLOT_TABS.get(selectedTabIndex);
        switch (entry) {
            case SidebarEntry.SlotSE se -> {
                ClientCosmeticState.get().unequip(se.slot());
                sendUnequipPacket(se.slot());
            }
            case SidebarEntry.WeaponTypeSE wte -> {
                ClientCosmeticState.get().unequipWeapon(wte.weaponType());
                sendUnequipWeaponPacket(wte.weaponType());
            }
            case SidebarEntry.WeaponHeaderSE ignored -> {}
        }
    }

    private void toggleForceEquip() {
        SidebarEntry entry = SLOT_TABS.get(selectedTabIndex);
        switch (entry) {
            case SidebarEntry.SlotSE se -> {
                if (!se.slot().isWeaponSlot()) return;
                boolean current = ClientCosmeticState.get().isForceEquip(se.slot());
                ClientCosmeticState.get().setForceEquip(se.slot(), !current);
                sendForceEquipPacket(se.slot(), !current);
            }
            case SidebarEntry.WeaponTypeSE wte -> {
                boolean current = ClientCosmeticState.get().isForceEquipWeapon(wte.weaponType());
                ClientCosmeticState.get().setForceEquipWeapon(wte.weaponType(), !current);
                sendForceEquipWeaponPacket(wte.weaponType(), !current);
            }
            case SidebarEntry.WeaponHeaderSE ignored -> {}
        }
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

    private void sendEquipPacket(CosmeticSlot slot, String id, boolean force) {
        dev.architectury.networking.NetworkManager.sendToServer(
            new io.github.manasmods.manas_cosmetics.network.WardrobePayloads.EquipPayload(slot.getId(), id, force));
    }

    private void sendUnequipPacket(CosmeticSlot slot) {
        dev.architectury.networking.NetworkManager.sendToServer(
            new io.github.manasmods.manas_cosmetics.network.WardrobePayloads.UnequipPayload(slot.getId()));
    }

    private void sendForceEquipPacket(CosmeticSlot slot, boolean value) {
        dev.architectury.networking.NetworkManager.sendToServer(
            new io.github.manasmods.manas_cosmetics.network.WardrobePayloads.ForceEquipPayload(slot.getId(), value));
    }

    private void sendEquipWeaponPacket(WeaponType wt, String id, boolean force) {
        dev.architectury.networking.NetworkManager.sendToServer(
            new io.github.manasmods.manas_cosmetics.network.WardrobePayloads.EquipWeaponPayload(wt.getId(), id, force));
    }

    private void sendUnequipWeaponPacket(WeaponType wt) {
        dev.architectury.networking.NetworkManager.sendToServer(
            new io.github.manasmods.manas_cosmetics.network.WardrobePayloads.UnequipWeaponPayload(wt.getId()));
    }

    private void sendForceEquipWeaponPacket(WeaponType wt, boolean value) {
        dev.architectury.networking.NetworkManager.sendToServer(
            new io.github.manasmods.manas_cosmetics.network.WardrobePayloads.ForceEquipWeaponPayload(wt.getId(), value));
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

    private static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, COL_BORDER_DK);
        g.fill(x,     y,     x + w,     y + h,     COL_BORDER_LT);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, COL_PANEL);
    }

    private List<CosmeticDefinition> getCosmeticsForEntry(SidebarEntry entry) {
        List<CosmeticDefinition> result = new ArrayList<>();
        for (CosmeticDefinition def : ClientCosmeticState.get().getAvailableCosmetics()) {
            switch (entry) {
                case SidebarEntry.SlotSE se -> { if (def.slot() == se.slot()) result.add(def); }
                case SidebarEntry.WeaponTypeSE wte -> {
                    if (def.slot() == CosmeticSlot.WEAPON && def.weaponType() == wte.weaponType())
                        result.add(def);
                }
                case SidebarEntry.WeaponHeaderSE ignored -> {}
            }
        }
        return result;
    }

    private boolean isEntryEquipped(SidebarEntry entry) {
        ClientCosmeticState state = ClientCosmeticState.get();
        return switch (entry) {
            case SidebarEntry.SlotSE se -> state.getEquipped(se.slot()).isPresent();
            case SidebarEntry.WeaponTypeSE wte -> state.getEquippedWeapon(wte.weaponType()).isPresent();
            case SidebarEntry.WeaponHeaderSE ignored -> false;
        };
    }

    private boolean isEquippedInList(SidebarEntry entry, CosmeticDefinition def) {
        ClientCosmeticState state = ClientCosmeticState.get();
        return switch (entry) {
            case SidebarEntry.SlotSE se -> state.isEquipped(se.slot(), def.id());
            case SidebarEntry.WeaponTypeSE wte -> state.isEquippedWeapon(wte.weaponType(), def.id());
            case SidebarEntry.WeaponHeaderSE ignored -> false;
        };
    }

    /** Returns true for sidebar entries that should be rendered as indented sub-items. */
    private static boolean isWeaponSubEntry(SidebarEntry entry) {
        if (entry instanceof SidebarEntry.WeaponTypeSE) return true;
        if (entry instanceof SidebarEntry.SlotSE se) {
            CosmeticSlot slot = se.slot();
            return slot == CosmeticSlot.SHIELD || slot == CosmeticSlot.GRIMOIRE || slot == CosmeticSlot.MAGIC_STAFF;
        }
        return false;
    }

    private String entryLabel(SidebarEntry entry) {
        return switch (entry) {
            case SidebarEntry.SlotSE se -> slotLabel(se.slot());
            case SidebarEntry.WeaponTypeSE wte -> weaponTypeLabel(wte.weaponType());
            case SidebarEntry.WeaponHeaderSE ignored -> "Weapon";
        };
    }

    private static String slotLabel(CosmeticSlot slot) {
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
            case AURA        -> "Aura";
        };
    }

    private static String weaponTypeLabel(WeaponType wt) {
        String id = wt.getId();
        // Convert snake_case to Title Case
        String[] parts = id.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0)));
                if (p.length() > 1) sb.append(p.substring(1));
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }

    @Override
    public void removed() {
        savedTabIndex      = selectedTabIndex;
        savedSlotScroll    = slotScrollOffset;
        savedListScroll    = listScrollOffset;
        savedCosmeticIndex = selectedCosmeticIndex;
        savedPresetIndex   = selectedPresetIndex;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
