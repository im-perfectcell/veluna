const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

const PAST_TS = () => new Date(Date.now() - 2000);
const serverTimestamp = () => PAST_TS();

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfilePayload(uid, overrides = {}) {
  return {
    userId: uid,
    displayName: "Alice Listener",
    email: "alice@example.com",
    photoUrl: "https://example.com/avatar.jpg",
    themePreset: "default",
    themeMode: "DARK",
    adaptToArtwork: true,
    cornerRadiusDp: 12,
    audioQuality: "HIGH",
    autoplay: true,
    loudnessNormalization: true,
    hideMusicVideos: false,
    selectedGenres: ["Indie", "Synthwave"],
    onboardingCompleted: true,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

function validLikedSongPayload(overrides = {}) {
  return {
    title: "Midnight City",
    artists: "M83",
    album: "Hurry Up, We're Dreaming",
    albumId: "MPREb_12345",
    artistId: "UC1234567890",
    duration: "4:03",
    durationSecs: 243,
    thumbnail: "https://lh3.googleusercontent.com/abc=w720-h720-l90-rj",
    explicit: false,
    likedAt: 1700000000000,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

function validPlaylistPayload(overrides = {}) {
  return {
    title: "Late Night Drive",
    description: "Synth and ambient picks",
    tracks: [
      {
        videoId: "dQw4w9WgXcQ",
        title: "Track One",
        artists: "Artist A",
      },
    ],
    clientUpdatedAt: 1700000000000,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

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

test("Unauthenticated caller cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("liked_songs").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("playlists").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("taste_seeds").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("hidden_artists").get()
  );
});

test("Owner can create, read, and update their profile; shadow fields and userId spoofing fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(profileRef.set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(profileRef.get());

  // Shadow update with unknown field must fail
  await assertFails(profileRef.update({ isAdmin: true, updatedAt: serverTimestamp() }));

  // Update mutable displayName succeeds
  await assertSucceeds(
    profileRef.update({ displayName: "Alice Updated", updatedAt: serverTimestamp() })
  );

  // Overwriting immutable createdAt must fail
  await assertFails(
    profileRef.update({ createdAt: serverTimestamp(), updatedAt: serverTimestamp() })
  );

  // Identity spoofing on create must fail
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set(validProfilePayload(ALICE_UID))
  );
});

test("Cross-user reads and writes are strictly rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("liked_songs")
      .doc("dQw4w9WgXcQ")
      .set(validLikedSongPayload())
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("liked_songs").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("liked_songs")
      .doc("dQw4w9WgXcQ")
      .delete()
  );
});

test("Owner can manage liked_songs, playlists, taste_seeds, and hidden_artists", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userDoc = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(
    userDoc.collection("liked_songs").doc("track_123").set(validLikedSongPayload())
  );
  await assertSucceeds(userDoc.collection("liked_songs").get());

  await assertSucceeds(
    userDoc.collection("playlists").doc("pl_night_drive").set(validPlaylistPayload())
  );
  await assertSucceeds(userDoc.collection("playlists").get());

  await assertSucceeds(
    userDoc.collection("taste_seeds").doc("seed_uc123").set({
      kind: "ARTIST",
      name: "Daft Punk",
      subtitle: "Electronic",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(
    userDoc.collection("hidden_artists").doc("artist_abc").set({
      name: "Some Artist",
      blockedAt: 1700000000000,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Invalid bounds or types fail
  await assertFails(
    userDoc.collection("liked_songs").doc("bad_track").set(validLikedSongPayload({ durationSecs: -5 }))
  );
  await assertFails(
    userDoc.collection("taste_seeds").doc("bad_kind").set({
      kind: "PODCAST",
      name: "Bad",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
});
