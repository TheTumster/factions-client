# RelapsedMC Client (1.21.11)

The official client for RelapsedMC factions. A free Fabric modpack: install it once, it updates itself.
(Code name "laG" stays internally: mod id, packages, repo.)

## What's in it
- **HUD**: FPS, ping, live TNT count, coords + facing, nether/overworld coords, potion timers, armor durability, faction member list (distance, arrow, coords)
- **Crumbs**: trails for every primed TNT (orange) and falling sand/gravel (yellow), red box where TNT exploded
- **Patch crumbs (RelapsedMC)**: TNT that hits your faction's claims shows up for you in purple, wherever you are
- **Team tracking (RelapsedMC)**: your online faction members show as a cyan pillar + in the HUD list
- Bundled in the pack: Litematica, MaLiLib, Litematica Printer, Sodium, Xaero's maps, Fabric API

Keys: **Right Shift** menu, **K** clear crumbs, **J** toggle crumbs. `/fc help`, `/fc reload`.

## How faction data works
The RelapsedMC server sends it. `laGCannons` (repo `TheTumster/lag-network`, `ClientLink.java`) looks up your
FactionsUUID faction and sends only your own faction's members and claim hits over the `relapsed:mates` and
`relapsed:crumbs` plugin channels. There are no keys in the pack, so nobody can pull another faction's coords.
On any other server nothing arrives and those features stay off.

## Install (players)
1. Install the Modrinth App (modrinth.com/app).
2. Download `RelapsedMC Client.mrpack` from the latest release
   (https://github.com/TheTumster/factions-client/releases/latest) and drag it into the app.
3. Hit Play, sign in with your Microsoft account like normal, join RelapsedMC.

## Schem share (`/ss`), private only
Schem share still uses the old Supabase backend with a group password, so it only works in the private
`RelapsedMC Client (team).mrpack` from the Actions tab. Never post that one publicly.
Setup: `supabase/schema.sql`, then the `FC_SUPABASE_URL`, `FC_SUPABASE_ANON_KEY`, `FC_TEAM_KEY` repo secrets.
