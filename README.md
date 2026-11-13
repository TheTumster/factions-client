# Factions Client (1.21.11)

Private factions client for me and my friends. Fabric mod + Supabase backend + one-file install.

## What's in it
- **HUD**: FPS, ping, live TNT count, coords + facing, nether/overworld coords, potion timers, armor durability, teammate list (distance, arrow, coords)
- **Crumbs**: trails for every primed TNT (orange) and falling sand/gravel (yellow), red box where TNT exploded
- **Patch crumbs (shared)**: explosions your teammates see show up for you in purple
- **Team tracking**: teammates on the same server show as a cyan pillar + in the HUD list
- **Schem share**: `/ss upload <file>`, `/ss get <code>`, `/ss list`
- Bundled in the pack: Litematica, MaLiLib, Litematica Printer, Sodium, Fabric API

Keys: **Right Shift** menu, **K** clear crumbs, **J** toggle crumbs. `/fc help`, `/fc reload`.

## One-time setup (owner)
1. Create a free project at supabase.com (separate from anything else).
2. SQL Editor -> paste `supabase/schema.sql` -> Run.
3. Project Settings -> API: copy the **Project URL** and the **anon public** key.
4. Make up a long random team key (your group password).
5. GitHub repo -> Settings -> Secrets and variables -> Actions -> add:
   `FC_SUPABASE_URL`, `FC_SUPABASE_ANON_KEY`, `FC_TEAM_KEY`
6. Actions tab -> latest "build" run -> download the **Factions-Client** artifact.

## Install (everyone)
1. Install the Modrinth App (modrinth.com/app).
2. Drag `Factions Client.mrpack` into it (or "Add instance" -> "Import from file").
3. Hit Play, sign in with your Microsoft account like normal.

The backend config is baked into the pack, so it works on first launch.
