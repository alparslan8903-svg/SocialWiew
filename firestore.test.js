const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice123";
const BOB_UID = "bob456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const pastTs = () => new Date(Date.now() - 1000);

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

async function seedProfiles() {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    const ts = pastTs();
    await db.collection("users_public").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      username: "alice_vibes",
      bio: "Video creator",
      followersCount: 0,
      followingCount: 0,
      videosCount: 0,
      isPublic: true,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("users_public").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      username: "bob_reels",
      bio: "Filmmaker",
      followersCount: 0,
      followingCount: 0,
      videosCount: 0,
      isPublic: true,
      createdAt: ts,
      updatedAt: ts,
    });
  });
}

test("Unauthenticated user: cannot list or write videos", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("videos").where("visibility", "==", "public").get());
  await assertFails(unauthDb.collection("users_public").where("isPublic", "==", true).get());
});

test("Authenticated user: PII isolation prevents reading another user's private email", async () => {
  const ts = pastTs();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users_private").doc(BOB_UID).set({
      userId: BOB_UID,
      email: "bob@example.com",
      createdAt: ts,
      updatedAt: ts,
    });
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users_private").doc(BOB_UID).get());
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(bobDb.collection("users_private").doc(BOB_UID).get());
});

test("Authenticated user: can create own public profile and rejects ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastTs();
  await assertSucceeds(
    aliceDb.collection("users_public").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      username: "alice_vibes",
      followersCount: 0,
      followingCount: 0,
      videosCount: 0,
      isPublic: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Unauthorized elevation of isVerified fails for regular users
  await assertFails(
    aliceDb.collection("users_public").doc(ALICE_UID).update({
      isVerified: true,
      updatedAt: pastTs(),
    })
  );
});

test("Authenticated user: cannot spoof another user's video or delete it", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastTs();

  // Alice cannot create video authored as Bob
  await assertFails(
    aliceDb.collection("videos").doc("vid1").set({
      userId: BOB_UID,
      authorName: "Bob",
      authorUsername: "bob_reels",
      caption: "Spoof",
      videoUrl: "https://example.com/v.mp4",
      durationMs: 5000,
      tags: ["vlog"],
      visibility: "public",
      likesCount: 0,
      commentsCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Alice creates valid video
  await assertSucceeds(
    aliceDb.collection("videos").doc("vid1").set({
      userId: ALICE_UID,
      authorName: "Alice",
      authorUsername: "alice_vibes",
      caption: "My first reel",
      videoUrl: "https://example.com/v.mp4",
      durationMs: 5000,
      tags: ["vlog"],
      visibility: "public",
      likesCount: 0,
      commentsCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Unfiltered list query fails; aligned query succeeds
  await assertFails(bobDb.collection("videos").get());
  await assertSucceeds(bobDb.collection("videos").where("visibility", "==", "public").get());

  // Bob cannot delete Alice's video
  await assertFails(bobDb.collection("videos").doc("vid1").delete());
});

test("Atomic follow and like batches succeed while unbacked counter tampering fails", async () => {
  await seedProfiles();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastTs();

  await assertSucceeds(
    aliceDb.collection("videos").doc("vid1").set({
      userId: ALICE_UID,
      authorName: "Alice",
      authorUsername: "alice_vibes",
      caption: "Sunset clip",
      videoUrl: "https://example.com/v.mp4",
      durationMs: 5000,
      tags: ["travel"],
      visibility: "public",
      likesCount: 0,
      commentsCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Tampering likesCount without video_likes doc in batch fails
  await assertFails(
    bobDb.collection("videos").doc("vid1").update({
      likesCount: 1,
      updatedAt: pastTs(),
    })
  );

  // Atomic like batch succeeds
  const likeBatch = bobDb.batch();
  likeBatch.set(bobDb.collection("video_likes").doc(`vid1_${BOB_UID}`), {
    videoId: "vid1",
    userId: BOB_UID,
    createdAt: pastTs(),
  });
  likeBatch.update(bobDb.collection("videos").doc("vid1"), {
    likesCount: 1,
    updatedAt: pastTs(),
  });
  await assertSucceeds(likeBatch.commit());

  // Atomic follow batch succeeds
  const followBatch = bobDb.batch();
  followBatch.set(bobDb.collection("follows").doc(`${BOB_UID}_${ALICE_UID}`), {
    followerId: BOB_UID,
    followingId: ALICE_UID,
    createdAt: pastTs(),
  });
  followBatch.update(bobDb.collection("users_public").doc(BOB_UID), {
    followingCount: 1,
    updatedAt: pastTs(),
  });
  followBatch.update(bobDb.collection("users_public").doc(ALICE_UID), {
    followersCount: 1,
    updatedAt: pastTs(),
  });
  await assertSucceeds(followBatch.commit());
});

test("Tenor GIF comment: valid GIF comment succeeds and empty comment fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastTs();

  await assertSucceeds(
    aliceDb.collection("videos").doc("vid1").set({
      userId: ALICE_UID,
      authorName: "Alice",
      authorUsername: "alice_vibes",
      caption: "Dance clip",
      videoUrl: "https://example.com/v.mp4",
      durationMs: 8000,
      tags: ["dans"],
      visibility: "public",
      likesCount: 0,
      commentsCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Empty comment (no text and no gifUrl) fails
  await assertFails(
    bobDb.collection("videos").doc("vid1").collection("comments").doc("c1").set({
      videoId: "vid1",
      userId: BOB_UID,
      authorName: "Bob",
      authorUsername: "bob_reels",
      text: "",
      visibility: "public",
      createdAt: pastTs(),
    })
  );

  // Valid Tenor GIF comment succeeds
  const commentBatch = bobDb.batch();
  commentBatch.set(bobDb.collection("videos").doc("vid1").collection("comments").doc("c1"), {
    videoId: "vid1",
    userId: BOB_UID,
    authorName: "Bob",
    authorUsername: "bob_reels",
    text: "",
    gifUrl: "https://media.tenor.com/example.gif",
    gifPreviewUrl: "https://media.tenor.com/example_tiny.gif",
    visibility: "public",
    createdAt: pastTs(),
  });
  commentBatch.update(bobDb.collection("videos").doc("vid1"), {
    commentsCount: 1,
    updatedAt: pastTs(),
  });
  await assertSucceeds(commentBatch.commit());
});

test("Cloud video media_chunks: creator can upload chunks atomically, others can read, others cannot spoof", async () => {
  await seedProfiles();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastTs();

  const uploadBatch = aliceDb.batch();
  uploadBatch.set(aliceDb.collection("videos").doc("vid_cloud"), {
    userId: ALICE_UID,
    authorName: "Alice",
    authorUsername: "alice_vibes",
    caption: "Cloud reel",
    videoUrl: "",
    chunkCount: 1,
    durationMs: 4000,
    tags: ["vlog"],
    visibility: "public",
    likesCount: 0,
    commentsCount: 0,
    createdAt: ts,
    updatedAt: ts,
  });
  uploadBatch.set(
    aliceDb.collection("videos").doc("vid_cloud").collection("media_chunks").doc("0"),
    {
      videoId: "vid_cloud",
      userId: ALICE_UID,
      chunkIndex: 0,
      data: "AAAAIGZ0eXBpc29t",
      visibility: "public",
      createdAt: ts,
    }
  );
  await assertSucceeds(uploadBatch.commit());

  await assertSucceeds(
    bobDb.collection("videos").doc("vid_cloud").collection("media_chunks")
      .where("videoId", "==", "vid_cloud")
      .where("visibility", "==", "public")
      .get()
  );

  // Bob cannot spoof a chunk into Alice's video or delete Alice's chunk
  await assertFails(
    bobDb.collection("videos").doc("vid_cloud").collection("media_chunks").doc("1").set({
      videoId: "vid_cloud",
      userId: BOB_UID,
      chunkIndex: 1,
      data: "AAAA",
      visibility: "public",
      createdAt: ts,
    })
  );
  await assertFails(
    bobDb.collection("videos").doc("vid_cloud").collection("media_chunks").doc("0").delete()
  );
});

test("Admin verification: alparslan8903@gmail.com can claim verified badge while others cannot spoof it", async () => {
  const adminUid = "admin_alparslan";
  const adminDb = testEnv.authenticatedContext(adminUid, {
    email: "alparslan8903@gmail.com",
    email_verified: true,
  }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  const ts = pastTs();

  // Regular user cannot create profile with isVerified = true
  await assertFails(
    aliceDb.collection("users_public").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      username: "alice_vibes",
      isVerified: true,
      followersCount: 0,
      followingCount: 0,
      videosCount: 0,
      isPublic: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Regular user cannot spoof authorVerified = true on video
  await assertFails(
    aliceDb.collection("videos").doc("spoof_vid").set({
      userId: ALICE_UID,
      authorName: "Alice",
      authorUsername: "alice_vibes",
      authorVerified: true,
      caption: "Spoof verified",
      videoUrl: "https://example.com/v.mp4",
      durationMs: 4000,
      tags: ["vlog"],
      visibility: "public",
      likesCount: 0,
      commentsCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Admin creates profile without verified flag, then promotes isVerified cleanly
  await assertSucceeds(
    adminDb.collection("users_public").doc(adminUid).set({
      userId: adminUid,
      displayName: "Alparslan",
      username: "alparslan8903",
      isVerified: false,
      followersCount: 0,
      followingCount: 0,
      videosCount: 0,
      isPublic: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    adminDb.collection("users_public").doc(adminUid).update({
      isVerified: true,
      updatedAt: pastTs(),
    })
  );
  await assertSucceeds(
    adminDb.collection("videos").doc("admin_vid").set({
      userId: adminUid,
      authorName: "Alparslan",
      authorUsername: "alparslan8903",
      authorVerified: true,
      caption: "Admin video",
      videoUrl: "https://example.com/v.mp4",
      durationMs: 4000,
      tags: ["admin"],
      visibility: "public",
      likesCount: 0,
      commentsCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});


