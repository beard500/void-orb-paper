# void-orb (Paper 1.21.11)

A throwable custom item for a Paper 1.21.11 Minecraft server. Looks like a
deep-black planet wrapped in a glowing Saturn-style ring — a "void orb."

**Key property:** players join from vanilla Minecraft clients and install
**nothing**. The custom 3D model is delivered by the server as an auto-sent
resource pack. The item polymorphs to (and is actually built on top of) a real
ender pearl, so the client renders the ender-pearl entity in flight with no
special handling.

## How it works

- **The item** is a real `minecraft:ender_pearl` stack with two extras: a
  `minecraft:item_model` component pointing at `void_orb:void_orb` (renders our
  custom 3D model via the resource pack), and a `PersistentDataContainer` tag
  that marks it as ours for event handlers.
- **Throwing it** spawns a vanilla `EnderPearl` entity (also PDC-tagged). The
  client sees a normal ender pearl arcing through the air — no client mod needed.
- **On impact**, a `ProjectileHitEvent` handler checks the PDC tag, cancels the
  vanilla hit (which would teleport + damage), spawns purple portal particles +
  an amethyst-break sound, and removes the pearl. Vanilla ender pearls from
  dispensers or `/give` are completely untouched.
- **The resource pack** is built by CI alongside the plugin JAR, published as a
  GitHub Release asset, and referenced by URL+SHA-1 in your server's
  `server.properties`.

Source: `src/main/java/com/beard500/voidorb/VoidOrbPlugin.java` (one file).

## Install on Minefort (or any Paper 1.21.11 host)

Every push to `main` triggers the CI workflow. When it goes green, a new
GitHub Release appears at
[github.com/beard500/void-orb-paper/releases](https://github.com/beard500/void-orb-paper/releases)
with a **paste-ready `server.properties` snippet** in the release notes and two
downloadable assets.

### Step 1 — Install the plugin JAR

1. Open the **latest release**.
2. Download `void-orb-0.1.0.jar`.
3. In the Minefort dashboard → **Files** → navigate to the `plugins/` folder.
4. Upload the JAR (or drop it in via SFTP: `ftp.minefort.com`).
5. Restart the server. In the log you should see:
   ```
   void_orb enabled — resource pack URL + sha1 must be set in server.properties for model to render
   ```

### Step 2 — Point the server at the resource pack

1. In the same GitHub Release, copy the three lines from the
   **"Minefort server.properties snippet"** box. They look like:
   ```properties
   resource-pack=https://github.com/beard500/void-orb-paper/releases/download/v0.1.0-rN/void-orb-pack.zip
   resource-pack-sha1=<40-char hex>
   require-resource-pack=true
   ```
2. In the Minefort dashboard → **Server Properties** (or open
   `server.properties` directly via the file manager).
3. Paste the three lines. If any of those keys already exist, overwrite them.
4. Restart the server.

**About `require-resource-pack=true`:** this forces clients to accept the pack
to join. If a player declines, they can't join. If you'd rather allow optional
accept (pearls appear as plain ender pearls for decliners), change to `false`.

### Step 3 — Test in-game

Op yourself, then run:
```
/voidorb give
```

Checklist:
- [ ] Inventory shows a 3D black orb with a glowing purple/white ring
  (pure ender-pearl icon → the resource pack didn't download or the client
  rejected it; see Troubleshooting)
- [ ] Held in hand, the orb tilts toward the camera (Saturn look)
- [ ] Right-click throws the orb; in flight it renders as a vanilla ender pearl
- [ ] On impact: purple portal particles, amethyst-break sound
- [ ] **No teleport**, **no fall damage** on the thrower
- [ ] Vanilla ender pearls (`/give @s ender_pearl`) still teleport normally

## Update loop

When we change anything (model, throw physics, cooldown), we push to `main`
and CI publishes a new Release with a new URL and a new SHA-1. To update the
live server:

1. Open the new Release, copy its updated snippet.
2. Paste over the old three lines in Minefort's `server.properties`.
3. (If the plugin JAR also changed) re-upload the JAR to `plugins/`.
4. Restart the server. On next join, clients auto-detect the new SHA-1 and
   re-download the pack.

## Troubleshooting

**Player sees a plain ender pearl, not the 3D model.**
- Did they accept the resource pack prompt on join? If not, reconnect.
- Is `resource-pack-sha1` in `server.properties` the exact 40-char hex from
  the Release notes (lowercase, no spaces)? A mismatch makes clients reject
  the download.
- Does the URL work in a browser (anonymous / incognito)? If not, GitHub may
  have rate-limited your server's IP — rare, usually self-heals.

**`/voidorb` says "Unknown command."**
- Did the server actually load the plugin? Check `logs/latest.log` for the
  "void_orb enabled" line. If missing, the JAR is in the wrong folder or has
  a name collision.

**Orb teleports me anyway.**
- That means our event handler didn't fire. Most likely cause: the PDC tag
  was stripped (e.g. via `/data` or an inventory-manipulation plugin), or the
  item was crafted from an ingredient instead of from `/voidorb give`. Only
  stacks created by the plugin carry the tag.

## Build from source

Requires Java 21. No Gradle install needed — the wrapper is pinned.

```bash
git clone https://github.com/beard500/void-orb-paper.git
cd void-orb-paper
python3 scripts/gen_textures.py     # generates the PNG textures
./gradlew build                      # produces plugin JAR + resource pack zip
```

Outputs:
- `build/libs/void-orb-0.1.0.jar`
- `build/pack/void-orb-pack.zip`
- `build/pack/void-orb-pack.sha1`

## Editing the 3D model

The geometry is at `src/resourcepack/assets/void_orb/models/item/void_orb.json` —
drop that file on Blockbench's start screen to reopen and edit. Export over the
same path, run `./gradlew build`, push. CI handles the rest. See
`blockbench/README.md`.

The textures are generated procedurally by `scripts/gen_textures.py` — edit the
pixel lists in that script to change colors or add nebula detail, or replace
the generated PNGs with hand-painted ones (just don't rerun the script if you
hand-paint; CI will overwrite them).

## Stack (verified)

| Component    | Version                            |
| ------------ | ---------------------------------- |
| Minecraft    | 1.21.11                            |
| Paper API    | `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT` |
| Gradle       | 9.4.1                              |
| Java         | 21                                 |
| pack_format  | 75                                 |

## Related

- **Fabric version** (different server loader, not deployable to this server):
  [beard500/void-orb](https://github.com/beard500/void-orb). Kept for reference;
  do not use on Paper.

## License

MIT.
