================================================================================
  MANAS COSMETICS — Cosmetic Pack Authoring Guide
================================================================================

Cosmetics are loaded from the server's config directory:
  config/manas_cosmetics/cosmetics/

Each cosmetic consists of two files placed in the same folder:
  1. <id>.json        — metadata / parameters
  2. <id>.bbmodel     — Blockbench model (geometry + texture + animations)

The mod syncs both files to the client on login, so players do not need to
install anything separately.

--------------------------------------------------------------------------------
  SIDECAR JSON FORMAT
--------------------------------------------------------------------------------

Required fields
---------------
  id               Namespaced identifier, e.g. "manas_cosmetics:icicle_wings"
  display_name     Human-readable name shown in the wardrobe GUI
  slot             Which slot the cosmetic occupies (see Slots section below)
  model            Path to the .bbmodel file relative to the cosmetics folder

Optional fields
---------------
  weapon_type      Restrict visibility to a weapon class (see Weapon Types below)
                   Omit or set to "any" to always show regardless of held item.
  force_equip_allowed
                   true  — players may toggle "Force Equip" so the cosmetic
                           shows even when the weapon_type condition isn't met
                   false — cosmetic obeys weapon_type unconditionally
                   Default: false

  scale            [x, y, z] multiplicative scale applied to the model.
                   Default: [1.0, 1.0, 1.0]

  offset           [x, y, z] positional offset in BBModel units (1/16 block).
                   Default: [0.0, 0.0, 0.0]

  rotation         [x, y, z] rotation in degrees applied after scale and offset.
                   Default: [180.0, 0.0, 0.0]
                   The default 180° X flip is required for most Blockbench models
                   because the standard export faces downward. Override this if
                   your model is already correctly oriented.

--------------------------------------------------------------------------------
  FULL TEMPLATE
--------------------------------------------------------------------------------

{
  "id": "manas_cosmetics:my_cosmetic",
  "display_name": "My Cosmetic",
  "slot": "back",
  "model": "my_cosmetic.bbmodel",

  "weapon_type": "any",
  "force_equip_allowed": false,

  "scale":    [1.0, 1.0, 1.0],
  "offset":   [0.0, 0.0, 0.0],
  "rotation": [180.0, 0.0, 0.0]
}

--------------------------------------------------------------------------------
  SLOTS
--------------------------------------------------------------------------------

  helmet        Rendered at the head (above it, on the helmet layer)
  above_head    Rendered floating above the head
  chestplate    Rendered at the torso front
  back          Rendered on the player's back  (wings, capes, backpacks, …)
  front         Rendered on the player's front (chest emblems, …)
  legs          Rendered at the upper legs
  boots         Rendered at the feet
  orbit         Rotates around the player continuously
  pet           Spawns a separate entity that follows the player
  weapon        Visible only when holding the weapon matching weapon_type
  shield        Shield slot
  grimoire      Grimoire slot
  magic_staff   Magic staff slot

--------------------------------------------------------------------------------
  WEAPON TYPES
--------------------------------------------------------------------------------

  any           Always visible (default)
  sword
  axe
  bow
  spear
  katana
  kodachi
  longsword
  hammer
  scythe
  kunai
  shield
  grimoire
  magic_staff

--------------------------------------------------------------------------------
  BLOCKBENCH MODEL REQUIREMENTS
--------------------------------------------------------------------------------

  - Export as "Generic Model" (.bbmodel) from Blockbench.
  - Embed the texture inside the .bbmodel file (Blockbench does this by default
    when you use File > Save Project — do NOT use Export > Java Entity Model).
  - The model origin (0, 0, 0) should be at the player's feet.
  - All geometry should use BBModel units (1 unit = 1/16 Minecraft block).
  - If your model faces downward in-game, set "rotation": [180, 0, 0] (this is
    the default; most Blockbench exports require it).
  - Animations defined inside the .bbmodel are played automatically on loop.

--------------------------------------------------------------------------------
  EXAMPLE — ICICLE WINGS
--------------------------------------------------------------------------------

File: config/manas_cosmetics/cosmetics/icicle_wings.json
--------------------------------------------------------------
{
  "id": "manas_cosmetics:icicle_wings",
  "display_name": "Icicle Wings",
  "slot": "back",
  "model": "icicle_wings.bbmodel",
  "scale":    [1.0, 1.0, 1.0],
  "offset":   [0.0, 0.0, 0.0],
  "rotation": [180.0, 0.0, 0.0]
}

File: config/manas_cosmetics/cosmetics/icicle_wings.bbmodel
--------------------------------------------------------------
  (binary — place the exported .bbmodel file here)

================================================================================
