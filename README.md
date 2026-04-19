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
- **The resource pack** is committed into this repo at `dist/void-orb-pack.zip`
  and served directly from `raw.githubusercontent.com` — a stable URL that
  doesn't redirect and has no expiring tokens.

Source: `src/main/java/com/beard500/voidorb/VoidOrbPlugin.java` (one file).

## Install on Minefort (or any Paper 1.21.11 host)

Two pieces go on the server: a plugin JAR (into `plugins/`) and three lines
into `server.properties`. That's it.

### Step 1 — Install the plugin JAR

1. Open the [**latest release**](https://github.com/beard500/void-orb-paper/releases/latest)
   and download `void-orb-0.1.0.jar`.
2. In the Minefort dashboard → **Files** → navigate to the `plugins/` folder.
   (Or use SFTP at `ftp.minefort.com`.)
3. If an older `void-orb-0.1.0.jar` is already there from a previous install,
   delete it first.
4. Upload the new JAR.
5. Restart the server.

In `logs/latest.log` you should now see:

```
[void-orb] Enabling void-orb v0.1.0
[void-orb] void_orb enabled — resource pack URL + sha1 must be set in server.properties for model to render
```

### Step 2 — Wire up the resource pack in `server.properties`

Open `server.properties` (Minefort dashboard → **Server Properties**, or the
file manager). Find these three keys and set them to exactly these values:

```properties
resource-pack=https://raw.githubusercontent.com/beard500/void-orb-paper/main/dist/void-orb-pack.zip
resource-pack-sha1=129c775e951435d30ab1a28975857656af32befd
require-resource-pack=true
```

If any of those three keys already exist, overwrite them. Save. Restart the
server.

This URL is stable forever — it will never change unless someone edits the
pack contents in this repo (in which case this README gets updated with the
new SHA-1). You do **not** need to re-paste this on every new plugin release.

**About `require-resource-pack=true`:** this forces clients to accept the pack
to join. If a player declines, they can't join. If you'd rather allow optional
accept (pearls appear as plain ender pearls for decliners), change this to
`false`.

### Step 3 — Test in-game

Op yourself (Minefort dashboard has a console button; use `op <yourname>` if
needed), then in-game type:

```
/voidorb give
```

You should get one Void Orb in your inventory.

- [ ] Inventory shows a 3D black orb with a glowing purple/white ring
  (if you see a plain ender-pearl icon, the resource pack didn't download
  or the client rejected it — see Troubleshooting)
- [ ] Held in hand, the orb tilts toward the camera (Saturn look)
- [ ] Right-click throws the orb; in flight it renders as a vanilla ender pearl
- [ ] On impact: purple portal particles, amethyst-break sound
- [ ] **No teleport** and **no fall damage** on the thrower
- [ ] Vanilla ender pearls (`/give @s ender_pearl`) still teleport normally

## Giving a void_orb to another player

```
/voidorb give <playername>
```

Requires the `voidorb.give` permission (granted to ops by default).

## Update loop

When we change something (model, throw physics, cooldown, texture) and push
to `main`:

- **Plugin JAR changed?** Grab the new `void-orb-0.1.0.jar` from the latest
  GitHub Release, re-upload it over the old one in `plugins/`, restart.
- **Pack contents changed?** The URL stays the same (always pointing at
  `main/dist/void-orb-pack.zip`), but the SHA-1 will differ. Update
  `resource-pack-sha1=` in `server.properties` to the new 40-char hex (the
  new value will be stated in the release notes and in this README's Step 2).
  Restart. Clients auto-detect the hash change and re-download the pack on
  next join.

Typical update only requires one of the two, not both.

## Troubleshooting

**`/voidorb` says "Unknown command."**
- Check `logs/latest.log` for the `[void-orb] Enabling void-orb v0.1.0`
  line. If it's missing, the JAR is in the wrong folder, has a name
  collision, or failed to load — scroll up in the log for a stacktrace.
- Make sure you're using `/voidorb give` (the plugin's command), not `/give`
  (the vanilla command, which doesn't know about our item).

**"Failed to download resource pack" when you join.**
- Open the URL from `server.properties` in a browser. If it doesn't download
  a ~3 KB zip, the URL is wrong.
- Confirm you're using the `raw.githubusercontent.com` URL above, not an
  older GitHub Releases URL (those have redirect issues).
- Confirm `resource-pack-sha1` exactly matches `129c775e951435d30ab1a28975857656af32befd`
  (lowercase, 40 hex chars, no spaces). A single-character mismatch breaks it.

**Player sees a plain ender pearl, not the 3D model.**
- Did they accept the "Server Resource Pack" prompt on join? If not,
  reconnect and accept.
- If `require-resource-pack=true` is set, clients that decline can't join at
  all — so seeing a plain pearl means the client accepted but something is
  still off. Check the client's `resource_packs/` cache for a stale or
  failed download; re-joining usually fixes it.

**Orb teleports me anyway.**
- Our event handler didn't fire. Most likely cause: the PDC tag was stripped
  (e.g. via `/data` or an inventory-manipulation plugin), or the item was
  crafted from an ingredient instead of received from `/voidorb give`. Only
  stacks created by the plugin carry the tag.

## Build from source

Requires Java 21. No separate Gradle install needed — the wrapper is pinned.

```bash
git clone https://github.com/beard500/void-orb-paper.git
cd void-orb-paper
python3 scripts/gen_textures.py     # regenerates the PNG textures
./gradlew build                      # produces plugin JAR + resource pack zip
```

Outputs:
- `build/libs/void-orb-0.1.0.jar`
- `build/pack/void-orb-pack.zip`
- `build/pack/void-orb-pack.sha1`

If you edit the pack and want Minefort to serve the new version, copy
`build/pack/void-orb-pack.zip` over `dist/void-orb-pack.zip`, note the new
SHA-1, commit + push, then update `resource-pack-sha1=` in `server.properties`
with the new hash.

## Editing the 3D model

The geometry is at `src/resourcepack/assets/void_orb/models/item/void_orb.json` —
drop that file on Blockbench's start screen to reopen and edit. Export over the
same path, rebuild, push. See `blockbench/README.md`.

The textures are generated procedurally by `scripts/gen_textures.py` — edit the
pixel lists in that script to change colors or add nebula detail, or replace
the generated PNGs with hand-painted ones (don't rerun the script if you
hand-paint; it will overwrite them).

## Stack (verified)

| Component    | Version                                            |
| ------------ | -------------------------------------------------- |
| Minecraft    | 1.21.11                                            |
| Paper API    | `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT` |
| Gradle       | 9.4.1                                              |
| Java         | 21                                                 |
| pack_format  | 75                                                 |

## Related

- **Fabric version** (different server loader, not deployable to this server):
  [beard500/void-orb](https://github.com/beard500/void-orb). Kept for reference;
  do not use on Paper.

## License

MIT.
