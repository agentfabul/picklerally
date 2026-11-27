# Pickle Rally Security Specification (Phase 6)

## Data Invariants
1. A player profile at `/users/{userId}` can only be created, read, or updated by `request.auth.uid == userId`, with `uid` and `createdAt` immutable, and `updatedAt == request.time`.
2. A room directory entry at `/rooms/{roomCode}` must use a valid `PKL-###` room code, have `hostUid == request.auth.uid` on creation, and reject updates once terminal (`HOST_LEFT` or `FINISHED`).
3. A match record at `/matches/{matchId}` can only be created by a signed-in participant (`request.auth.uid in participants`, bounded `1..4`), MUST start with `validated == false`, and MUST satisfy official pickleball victory math (`max(scoreLower, scoreUpper) >= 11`, margin `>= 2`, matching `winnerSide`). Client updates and deletes on `/matches` are forbidden; only Firebase Cloud Functions set `validated: true`.
4. A public leaderboard card at `/leaderboard/{userId}` contains zero PII (`uid`, `displayName`, `characterId`, `wins`, `losses`, `matchesPlayed`, `pointsScored`, `bestRally`, `rating`, `isPublic`, `updatedAt`), enforces `resource.data.isPublic == true` on `get`/`list`, and limits owner stat increments to at most 1 match per write (`wins <= existing().wins + 1`, `matchesPlayed == wins + losses`).
5. In Realtime Database (`database.rules.json`), players can ONLY write their own `inputs/{uid}` and host can ONLY write `state`.

## Adversarial Payloads ("Dirty Dozen" Red Team Matrix)
1. Unauthenticated read/write to `/users/u1` -> `PERMISSION_DENIED`
2. Authenticated `u2` reading or overwriting `/users/u1` -> `PERMISSION_DENIED`
3. Creating `/users/u1` with shadow field `isAdmin: true` -> `PERMISSION_DENIED`
4. Creating `/users/u1` with forged `createdAt` timestamp (not `request.time`) -> `PERMISSION_DENIED`
5. Updating `/users/u1` to mutate immutable `uid` -> `PERMISSION_DENIED`
6. Creating `/matches/m1` with `validated: true` directly from client -> `PERMISSION_DENIED`
7. Creating `/matches/m1` with invalid pickleball score `11 - 10` (not win by 2) -> `PERMISSION_DENIED`
8. Creating `/matches/m1` with `participants: []` or without `request.auth.uid` -> `PERMISSION_DENIED`
9. Updating or deleting `/matches/m1` from client SDK -> `PERMISSION_DENIED`
10. Updating `/leaderboard/u2` while authenticated as `u1` -> `PERMISSION_DENIED`
11. Updating own `/leaderboard/u1` to jump `wins` by `+50` in a single write -> `PERMISSION_DENIED`
12. Blanket `list` query on `/users` or `/matches` without ownership/participant filter -> `PERMISSION_DENIED`
