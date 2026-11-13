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

function validPayload(uid, overrides = {}) {
  return {
    userId: uid,
    category: "Plastic",
    itemName: "PET Water Bottle",
    confidence: 0.94,
    isUncertain: false,
    disposalMethod: "Rinse, remove cap, and place in the Blue Plastic Recycling bin.",
    binColor: "Blue Recycling Bin",
    ecoTip: "Flattening bottles saves 60% bin space.",
    sourceType: "Camera",
    createdAt: new Date(Date.now() - 1000),
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

test("Unauthenticated user: cannot list or read classifications", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("classifications").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("classifications").doc("scan_1").get());
});

test("Authenticated owner: can create, list, update category, and delete own classification", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("classifications").doc("scan_1");

  await assertSucceeds(docRef.set(validPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("classifications").get());
  await assertSucceeds(docRef.update({ category: "Glass", isUncertain: false }));
  await assertSucceeds(docRef.delete());
});

test("Cross-user isolation: Bob cannot read, create, update, or delete Alice's classifications", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("classifications").doc("scan_1").set(
      validPayload(ALICE_UID, { category: "Metal", itemName: "Aluminum Can" })
    );
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceDocRef = bobDb.collection("users").doc(ALICE_UID).collection("classifications").doc("scan_1");

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("classifications").get());
  await assertFails(aliceDocRef.get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("classifications").doc("scan_2").set(validPayload(BOB_UID)));
  await assertFails(aliceDocRef.update({ category: "Other" }));
  await assertFails(aliceDocRef.delete());
});

test("Schema validation: rejects invalid category, out-of-range confidence, and shadow ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const col = aliceDb.collection("users").doc(ALICE_UID).collection("classifications");

  await assertFails(col.doc("bad_cat").set(validPayload(ALICE_UID, { category: "Hazardous" })));
  await assertFails(col.doc("bad_conf").set(validPayload(ALICE_UID, { confidence: 1.5 })));
  await assertFails(col.doc("ghost_field").set(validPayload(ALICE_UID, { isAdmin: true })));
  await assertFails(col.doc("spoof_uid").set(validPayload(BOB_UID)));
});
