# void-orb (Paper 1.21.11)

A throwable custom item for a Paper 1.21.11 Minecraft server. A "void orb" — a
deep-black planet wrapped in a glowing Saturn-style ring.

**Current version: 0.2.1.** The orb has full gameplay behavior (throw-and-return,
teleport, levitation, dragon drop). v0.2.1 fixes the flight physics so the orb
flies like a vanilla ender pearl and pauses for 3 seconds where it lands before
returning. See "What it does" below.

**Key property:** players join from vanilla Minecraft clients and install
**nothing**. The custom 3D model is delivered by the server as an auto-sent
resource pack.

## What it does

### Right-click — throw
- Throws the orb just like a vanilla ender pearl (gravity, arc, same speed).
  You can aim straight out, up, or down — it flies like a real pearl.
- The orb **pierces through enemies**, damaging every living thing it passes.
  Damage is about the same as a fully-charged Power III bow arrow (~9 damage,
  or 4.5 hearts), and it ignores armor.
- When the orb hits a block or reaches max distance (~48 blocks), it **stops
  and hovers at that spot for 3 seconds**.
- After the 3-second pause, the orb **flies back to you at the same speed it
  was thrown**, like a loyalty trident. It disappears when it reaches you.
- The orb is never used up. You keep it forever.

### Left-click while the orb is in the air — teleport
- You instantly teleport to **wherever the orb is at that moment**.
- Works during all three phases: while the orb is flying out, while it's
  hovering at its landing spot, and while it's flying back to you.
- Works with left-click on air, blocks, or mobs (same click, same teleport).
- Zero fall damage on landing.
- After a teleport, there's a **10-second cooldown** before you can throw
  again. You'll see a cooldown bar on the item.

### Left-click an enemy (orb NOT in the air) — levitation
- The enemy floats up with **Levitation for 5 seconds**.
- 3-second cooldown between uses.
- Your regular fist damage still applies on top.

### Other stuff
- **Guaranteed drop** from the Ender Dragon. Kill the dragon, get an orb on
  the bedrock portal.
- **Drops on death** like any other item. If someone kills you, the orb is
  on the ground where you died and they can pick it up.
- **Infinite durability** — the orb never breaks and is never consumed.

### Cooldown rules — quick reference

| What you did                                  | Can you throw again?                       |
| --------------------------------------------- | ------------------------------------------ |
| Orb flew out, paused, and came back on its own | Yes, immediately                          |
| You left-clicked during flight to teleport     | No, wait 10 seconds                       |
| Orb is still out there somewhere              | No, wait for it to come back or teleport |

## Upgrading to v0.2.1 on your server

If you have a previous version running (v0.1.0 or v0.2.0), here's the upgrade
procedure. **One thing changes, one thing does NOT change.**

### What changes
1. **Swap the plugin JAR.**
   - Open your Minefort dashboard → **Files** → `plugins/` folder.
   - **Delete** the old JAR (whichever version is there: `void-orb-0.1.0.jar`
     or `void-orb-0.2.0.jar`).
   - Upload the new `void-orb-0.2.1.jar` from
     [the latest release](https://github.com/beard500/void-orb-paper/releases/latest).
2. **Restart the server.**

That's it.

### What does NOT change

**Do not touch `server.properties`.** The resource pack URL and SHA-1 are
**identical** across all versions (no pack content has changed). Your
existing three lines stay exactly as they are:

```properties
resource-pack=https://raw.githubusercontent.com/beard500/void-orb-paper/main/dist/void-orb-pack.zip
resource-pack-sha1=129c775e951435d30ab1a28975857656af32befd
require-resource-pack=true
```

If you see these already, leave them alone.

### How to confirm the new version is running

After restart, open `logs/latest.log` in the Minefort file viewer. You should
see:

```
[void-orb] Enabling void-orb v0.2.1
[void-orb] void_orb enabled — resource pack URL + sha1 must be set in server.properties for model to render
```

The `v0.2.1` bit is how you know the upgrade worked. If it still says an
older version, the old JAR is still in `plugins/` — delete it and try again.

## Installing fresh (if this is your first time)

Skip this if you're upgrading. Two pieces go on the server: the plugin JAR,
and three lines in `server.properties`.

### Step 1 — Install the plugin JAR
1. Open the [**latest release**](https://github.com/beard500/void-orb-paper/releases/latest)
   and download `void-orb-0.2.1.jar`.
2. Minefort dashboard → **Files** → `plugins/` folder.
3. Upload the JAR.
4. Restart the server.

You should see `[void-orb] Enabling void-orb v0.2.1` in `logs/latest.log`.

### Step 2 — Wire up the resource pack
Open `server.properties` (Minefort dashboard → **Server Properties**). Find
these three keys and set them to exactly these values:

```properties
resource-pack=https://raw.githubusercontent.com/beard500/void-orb-paper/main/dist/void-orb-pack.zip
resource-pack-sha1=129c775e951435d30ab1a28975857656af32befd
require-resource-pack=true
```

Save. Restart.

**About `require-resource-pack=true`:** this forces clients to accept the
pack to join. If a player declines, they can't join. If you'd rather make it
optional (decliners see a plain ender-pearl icon), change this to `false`.

### Step 3 — Give yourself an orb

In-game as an op:

```
/voidorb give
```

You should get one Void Orb in your inventory.

## Testing in-game (sanity checks)

Go through this list after each install/upgrade. If something doesn't work,
note which item — that tells us what to fix.

### The basics
- [ ] Inventory shows a 3D black orb with a glowing purple/white ring. (If
      you see a plain ender-pearl icon, the resource pack didn't download —
      see Troubleshooting.)
- [ ] You can throw the orb many times and it always stays at 1 in your
      inventory (infinite durability).

### Throw and return (no left-clicks)
- [ ] Right-click **into the ground**: orb flies like a vanilla pearl, sticks
      exactly where it hits (no skidding), hovers there for 3 seconds with
      small particle pulses, then flies back to you at the same speed.
- [ ] Right-click **straight out horizontally**: orb arcs with gravity, falls
      to the ground somewhere ahead, pauses 3 seconds, flies back. You do
      NOT teleport to yourself.
- [ ] Right-click **straight up**: orb arcs up, comes back down with gravity,
      pauses where it lands for 3 seconds, flies back. You do NOT teleport
      to yourself.
- [ ] Right-click **into a wall**: orb sticks at the wall, pauses 3 seconds,
      flies back.
- [ ] After the orb returns on its own (any of the above), you can throw
      again **right away** (no cooldown bar appears).

### Piercing damage
- [ ] Line up 3 zombies, throw through them: all 3 take damage, the orb
      keeps flying past them, then returns.
- [ ] Damage is roughly 4.5 hearts per zombie.
- [ ] An armored mob takes the same damage as an unarmored one (orb damage
      ignores armor).

### Teleport (left-click in all three flight phases)
- [ ] **During OUTBOUND (orb arcing through the air):** throw, left-click
      mid-arc → teleport to wherever the orb is at that instant. No fall
      damage.
- [ ] **During LANDED (orb hovering at its landing spot):** throw, wait
      for it to land, left-click any time during the 3-second pause →
      teleport to the landed spot.
- [ ] **During RETURNING (orb flying back):** throw, wait for the 3-second
      pause to finish, left-click while the orb is on its way back →
      teleport to wherever it is on the return path.
- [ ] Left-click on air, a block, or a mob all work the same (teleport).
      On a mob: the mob does NOT take melee damage from that swing.
- [ ] After teleporting, the orb shows a cooldown bar for ~10 seconds.
      Right-click during cooldown does nothing.

### Levitation (left-click when no orb is in the air)
- [ ] Left-click a zombie with no orb in flight → zombie floats up for
      5 seconds. Your fist damage also registers.
- [ ] Left-click the zombie again within 3 seconds → no new levitation, but
      your fist still does damage.
- [ ] Wait 3 seconds, left-click again → levitation comes back.

### Dragon drop
- [ ] On an End test world, kill the Ender Dragon → exactly one Void Orb
      drops on the bedrock portal platform.

### Edge cases (should not crash or leave ghosts)
- [ ] Throw orb, log out mid-flight, log back in → no ghost orb floating in
      the air.
- [ ] Throw orb, die mid-flight → respawn, no ghost orb, everything normal.
- [ ] Throw orb, step through an End portal → nothing bad happens; orb
      flight ends.

### Regression checks (old behavior still works)
- [ ] Vanilla ender pearls (`/give @s ender_pearl`) still teleport normally
      when you throw them. The Void Orb and vanilla pearls live side-by-side.
- [ ] The cooldown bar after a teleport shows only on the Void Orb, not on
      vanilla ender pearls in the same inventory.

## Reverting if something breaks

All previous versions stay on GitHub forever. To roll back:

1. Open the [releases page](https://github.com/beard500/void-orb-paper/releases)
   and find the version you want to fall back to:
   - `v0.2.0-r*` — gameplay orb, older flight physics (fast straight-line, no
     landed pause). Use this if v0.2.1's new flight behavior is the problem.
   - `v0.1.0-r*` — the original cosmetic-only orb (no gameplay). Use this if
     anything in the gameplay layer is causing trouble.
2. Download the matching JAR (`void-orb-0.2.0.jar` or `void-orb-0.1.0.jar`).
3. Minefort dashboard → **Files** → `plugins/`.
4. **Delete** the current JAR (`void-orb-0.2.1.jar`).
5. Upload the older JAR.
6. Restart.

`server.properties` does NOT need any changes on any rollback — the resource
pack SHA-1 is the same across every version.

If you want to be extra safe before upgrading: download the old JAR to your
computer first so you always have a local copy to re-upload if the new
version misbehaves.

## Giving a Void Orb to another player

```
/voidorb give <playername>
```

Requires the `voidorb.give` permission (granted to ops by default).

## Troubleshooting

**`/voidorb` says "Unknown command."**
- Check `logs/latest.log` for `[void-orb] Enabling void-orb v0.2.1`. If
  missing, the JAR is in the wrong folder, has a name collision, or failed
  to load — scroll up in the log for a stacktrace.
- Use `/voidorb give`, not `/give` (the vanilla command doesn't know about
  our item).

**"Failed to download resource pack" when you join.**
- Open the URL from `server.properties` in a browser. It should download a
  ~3 KB zip. If it doesn't, the URL is wrong.
- Confirm `resource-pack-sha1` exactly matches
  `129c775e951435d30ab1a28975857656af32befd` — lowercase, 40 characters, no
  spaces.

**Player sees a plain ender pearl, not the 3D model.**
- Did they accept the "Server Resource Pack" prompt on join? Reconnect and
  accept.
- Try clearing the client's `resource_packs/` cache and re-joining.

**Orb teleports me like a vanilla pearl instead of piercing.**
- The PDC tag got stripped (e.g. via `/data` or another plugin messing with
  inventory NBT). Drop the tagged orb and give yourself a fresh one with
  `/voidorb give`.
- Make sure v0.2.1 is actually running (`logs/latest.log`).

**Right-click does nothing.**
- Is the cooldown bar showing? Wait for it to finish (up to 10 seconds after
  a teleport).
- Is an orb already in flight? You can only have one out at a time. Wait
  for it to return, or left-click to teleport to it.

**Left-click doesn't teleport me.**
- The orb has to be in the air. If it already returned, your next left-click
  will levitate whatever you're hitting, not teleport.
- You must be holding the Void Orb in your **main hand** (not off-hand).

**Enemy doesn't get Levitation when I left-click.**
- If an orb is in the air, left-click teleports instead of levitates —
  that's by design.
- Check the 3-second cooldown between levitations.
- The target has to be a living entity (mob or player), not an armor stand
  or item frame.

**Dragon didn't drop a Void Orb.**
- Only the Ender Dragon (`EnderDragon`), not any other mob.
- Confirm v0.2.1 is running. v0.1.0 didn't have this feature.

**I got stuck in a wall after teleporting.**
- This can happen if the orb passed **into** a block before you clicked.
  Use `/tp` to get unstuck and try again. (Known rough edge; may tweak the
  teleport-destination logic in a future version.)

## Update loop (for the maintainer)

When we change something and push to `main`:

- **Plugin JAR changed?** A new GitHub Release appears with a new
  `void-orb-X.Y.Z.jar`. Delete the old JAR, upload the new one, restart.
  `server.properties` usually stays untouched.
- **Pack contents changed?** The SHA-1 will differ. Update
  `resource-pack-sha1=` in `server.properties` to the new 40-character hex
  (it'll be in the release notes and in this README's "Installing fresh"
  section). Restart. Clients auto-re-download the pack.

Most updates touch only the JAR.

## Build from source

Requires Java 21 and Gradle 9.4.1 (on macOS: `brew install openjdk@21 gradle`).

```bash
git clone https://github.com/beard500/void-orb-paper.git
cd void-orb-paper
python3 scripts/gen_textures.py     # regenerates the PNG textures
gradle build -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@21
```

Outputs:
- `build/libs/void-orb-0.2.1.jar`
- `build/pack/void-orb-pack.zip`
- `build/pack/void-orb-pack.sha1`

If you edit the pack and want the server to serve the new version, copy
`build/pack/void-orb-pack.zip` over `dist/void-orb-pack.zip`, note the new
SHA-1, commit + push, then update `resource-pack-sha1=` in `server.properties`.

## Editing the 3D model

Geometry: `src/resourcepack/assets/void_orb/models/item/void_orb.json` —
drop that file on Blockbench's start screen to edit. Export over the same
path, rebuild, push. See `blockbench/README.md`.

Textures: generated by `scripts/gen_textures.py` — edit the pixel lists or
replace the PNGs with hand-painted ones (don't rerun the script if you
hand-paint; it will overwrite them).

## Source layout

| Path                                                          | What it is                                                  |
| ------------------------------------------------------------- | ----------------------------------------------------------- |
| `src/main/java/com/beard500/voidorb/VoidOrbPlugin.java`       | The whole plugin: item factory, flight system, event hooks. |
| `src/main/resources/plugin.yml`                               | Plugin registration + `/voidorb` command + permission.      |
| `src/resourcepack/`                                           | Resource pack source (model, textures, pack.mcmeta).        |
| `dist/void-orb-pack.zip`                                      | Pre-built pack served over raw.githubusercontent.com.       |
| `scripts/gen_textures.py`                                     | Procedurally regenerates the PNG textures.                  |
| `.github/workflows/release.yml`                               | CI: builds JAR + pack, cuts a GitHub Release on push.       |

## Stack (verified)

| Component    | Version                                            |
| ------------ | -------------------------------------------------- |
| Minecraft    | 1.21.11                                            |
| Paper API    | `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT` |
| Gradle       | 9.4.1                                              |
| Java         | 21                                                 |
| pack_format  | 75                                                 |

## Related

- **Fabric version** (different server loader, not deployable to this
  server): [beard500/void-orb](https://github.com/beard500/void-orb). Kept
  for reference; do not use on Paper.

## License

MIT.
