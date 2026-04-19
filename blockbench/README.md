# Blockbench source

The `.bbmodel` source wasn't exported in the original build session (the MCP
export path hung, so the model JSON was written by hand from cube coordinates
instead). The canonical geometry lives in
`../src/main/resources/assets/void_orb/models/item/void_orb.json` — you can
drop that JSON onto Blockbench's start screen to reopen and edit it.

To re-export after editing in Blockbench:

1. File → Export → Export Block/Item Model → overwrite the JSON above
2. File → Export → Export Texture → overwrite the PNGs in
   `../src/main/resources/assets/void_orb/textures/item/`
   (or re-run `python3 scripts/gen_textures.py` from the repo root if you
   edited `gen_textures.py` instead of the raw PNGs)
3. Rebuild: `gradle build`

If you make structural changes (add/remove cubes), update
`scripts/gen_textures.py` too so CI produces the textures that match.
