const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: denied across users, matches, and leaderboard", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("matches").where("participants", "array-contains", ALICE_UID).get());
  await assertFails(unauthDb.collection("leaderboard").where("isPublic", "==", true).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Anon",
      characterId: "RIO",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authenticated owner: users profile create, partial character/name/stats update, and immutability guards", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Create profile mirroring Kotlin StatsRepository
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Alice",
      characterId: "RIO",
      wins: 0,
      losses: 0,
      matchesPlayed: 0,
      pointsScored: 0,
      bestRally: 0,
      rating: 1000,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update: characterId only
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      characterId: "DILL",
      updatedAt: new Date(Date.now() - 5000),
    })
  );

  // Partial update: post-match stats increment
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      characterId: "DILL",
      wins: 1,
      losses: 0,
      matchesPlayed: 1,
      pointsScored: 11,
      bestRally: 9,
      rating: 1010,
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Reject createdAt mutation & future timestamp & extra @DocumentId 'id'
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Future",
      updatedAt: new Date(Date.now() + 60000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).set({
      uid: BOB_UID,
      displayName: "CrossUser",
      characterId: "RIO",
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("Cross-user isolation on users/{uid}", async () => {
  const created = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(BOB_UID).set({
      uid: BOB_UID,
      displayName: "Bob",
      characterId: "LOLA",
      wins: 0,
      losses: 0,
      matchesPlayed: 0,
      pointsScored: 0,
      bestRally: 0,
      rating: 1000,
      createdAt: created,
      updatedAt: created,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).update({
      displayName: "Hacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Matches collection: Kotlin create, win-by-2 bounds, array-contains query, and immutable update/delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("matches").doc("m-1001").set({
      matchId: "m-1001",
      mode: "SOLO_1V1",
      scoringMode: "RALLY",
      createdByUid: ALICE_UID,
      participants: [ALICE_UID],
      playerCharacter: "RIO",
      opponentCharacter: "LOLA",
      winnerSide: "LOWER",
      scoreLower: 11,
      scoreUpper: 7,
      bestRally: 12,
      validated: false,
      createdAt: created,
    })
  );

  // Scoped query matching Kotlin StatsRepository
  await assertSucceeds(
    aliceDb.collection("matches").where("participants", "array-contains", ALICE_UID).limit(25).get()
  );

  // Unfiltered query rejected
  await assertFails(aliceDb.collection("matches").get());

  // Forged win-by-1 score rejected
  await assertFails(
    aliceDb.collection("matches").doc("m-1002").set({
      matchId: "m-1002",
      mode: "SOLO_1V1",
      scoringMode: "RALLY",
      createdByUid: ALICE_UID,
      participants: [ALICE_UID],
      playerCharacter: "RIO",
      opponentCharacter: "LOLA",
      winnerSide: "LOWER",
      scoreLower: 11,
      scoreUpper: 10,
      bestRally: 5,
      validated: false,
      createdAt: created,
    })
  );

  // Forged validated=true rejected
  await assertFails(
    aliceDb.collection("matches").doc("m-1003").set({
      matchId: "m-1003",
      mode: "SOLO_1V1",
      scoringMode: "RALLY",
      createdByUid: ALICE_UID,
      participants: [ALICE_UID],
      playerCharacter: "RIO",
      opponentCharacter: "LOLA",
      winnerSide: "LOWER",
      scoreLower: 11,
      scoreUpper: 5,
      bestRally: 4,
      validated: true,
      createdAt: created,
    })
  );

  // Update and delete denied
  await assertFails(aliceDb.collection("matches").doc("m-1001").update({ scoreLower: 15 }));
  await assertFails(aliceDb.collection("matches").doc("m-1001").delete());
});

test("Leaderboard collection: create, bounded update, cross-user denial, and public query", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const updated = new Date(Date.now() - 4000);

  await assertSucceeds(
    aliceDb.collection("leaderboard").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Alice",
      characterId: "RIO",
      wins: 1,
      losses: 0,
      matchesPlayed: 1,
      pointsScored: 11,
      bestRally: 9,
      rating: 1010,
      isPublic: true,
      updatedAt: updated,
    })
  );

  await assertSucceeds(
    aliceDb.collection("leaderboard").doc(ALICE_UID).update({
      displayName: "AlicePro",
      characterId: "MAYA",
      wins: 2,
      losses: 0,
      matchesPlayed: 2,
      pointsScored: 22,
      bestRally: 14,
      rating: 1020,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Scoped query matching Kotlin StatsRepository
  await assertSucceeds(
    aliceDb.collection("leaderboard").where("isPublic", "==", true).limit(50).get()
  );

  // Rating jump > 30 rejected
  await assertFails(
    aliceDb.collection("leaderboard").doc(ALICE_UID).update({
      displayName: "AlicePro",
      characterId: "MAYA",
      wins: 3,
      losses: 0,
      matchesPlayed: 3,
      pointsScored: 33,
      bestRally: 14,
      rating: 1500,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user leaderboard write rejected
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("leaderboard").doc(ALICE_UID).update({
      displayName: "Hacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
