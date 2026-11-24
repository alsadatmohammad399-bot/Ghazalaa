const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_owner_123";
const BOB_UID = "bob_other_456";
const ADMIN_UID = "ghazalo_admin_789";
const ADMIN_EMAIL = "alsadatmohammad399@gmail.com";
const REST_ID = "rest_zain_01";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validRestaurantPayload(ownerId, ownerEmail) {
  const now = new Date(Date.now() - 2000);
  return {
    ownerId,
    ownerEmail,
    nameAr: "مطعم زين الشام",
    nameEn: "Zain El-Sham Grill",
    slug: "zain-el-sham",
    phone: "01015861991",
    phoneVerified: true,
    templateId: "oriental_royal",
    currency: "EGP",
    subscriptionStatus: "TRIAL",
    planTier: "FREE_TRIAL",
    trialDaysRemaining: 7,
    autoBackupEnabled: true,
    soundAlertEnabled: true,
    createdAt: now,
    updatedAt: now,
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

test("Unauthenticated user: cannot read or write restaurants or users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("restaurants").get());
  await assertFails(
    unauthDb.collection("restaurants").doc(REST_ID).set(validRestaurantPayload(ALICE_UID, "alice@test.com"))
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("UserProfile: owner can create/update own profile; cross-user blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@test.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@test.com", email_verified: true }).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Owner",
      email: "alice@test.com",
      phone: "01012345678",
      phoneVerified: false,
      preferredLanguage: "ar",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      phone: "01099887766",
      phoneVerified: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("RestaurantProfile: owner creates trial, cannot self-activate; admin can activate; unverified admin email rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@test.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@test.com", email_verified: true }).firestore();
  const spoofAdminDb = testEnv.authenticatedContext("spoof_uid", { email: ADMIN_EMAIL, email_verified: false }).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();

  // Reject self-activated creation
  const illegalCreate = validRestaurantPayload(ALICE_UID, "alice@test.com");
  illegalCreate.subscriptionStatus = "ACTIVE";
  await assertFails(aliceDb.collection("restaurants").doc(REST_ID).set(illegalCreate));

  // Valid trial creation
  await assertSucceeds(
    aliceDb.collection("restaurants").doc(REST_ID).set(validRestaurantPayload(ALICE_UID, "alice@test.com"))
  );

  // Reject ghost field / @DocumentId `id` field
  const ghostPayload = validRestaurantPayload(ALICE_UID, "alice@test.com");
  ghostPayload.id = "rest_ghost";
  await assertFails(aliceDb.collection("restaurants").doc("rest_ghost").set(ghostPayload));

  // Owner updates templateId (allowed)
  await assertSucceeds(
    aliceDb.collection("restaurants").doc(REST_ID).update({
      templateId: "luxury_gold",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Owner submits payment reference -> PENDING_PAYMENT (allowed)
  await assertSucceeds(
    aliceDb.collection("restaurants").doc(REST_ID).update({
      subscriptionStatus: "PENDING_PAYMENT",
      planTier: "PRO",
      paymentReference: "INSTAPAY-9921",
      updatedAt: new Date(Date.now() - 900),
    })
  );

  // Owner cannot self-promote to ACTIVE
  await assertFails(
    aliceDb.collection("restaurants").doc(REST_ID).update({
      subscriptionStatus: "ACTIVE",
      updatedAt: new Date(Date.now() - 800),
    })
  );

  // Spoofed unverified admin email cannot activate or list
  await assertFails(spoofAdminDb.collection("restaurants").get());
  await assertFails(
    spoofAdminDb.collection("restaurants").doc(REST_ID).update({
      subscriptionStatus: "ACTIVE",
      updatedAt: new Date(Date.now() - 700),
    })
  );

  // Verified admin can list all and activate account
  await assertSucceeds(adminDb.collection("restaurants").get());
  await assertSucceeds(
    adminDb.collection("restaurants").doc(REST_ID).update({
      subscriptionStatus: "ACTIVE",
      planTier: "PRO",
      trialDaysRemaining: 30,
      updatedAt: new Date(Date.now() - 600),
    })
  );

  // Cross-user read/update blocked
  await assertFails(bobDb.collection("restaurants").doc(REST_ID).get());
  await assertFails(bobDb.collection("restaurants").get());
});

test("Subcollections: menu_items, orders (with terminal state lock), inventory, staff & filtered queries", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@test.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@test.com", email_verified: true }).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const now = new Date(Date.now() - 3000);

  await assertSucceeds(
    aliceDb.collection("restaurants").doc(REST_ID).set(validRestaurantPayload(ALICE_UID, "alice@test.com"))
  );

  const restRef = aliceDb.collection("restaurants").doc(REST_ID);

  // Menu item create + update + filtered query
  await assertSucceeds(
    restRef.collection("menu_items").doc("item_1").set({
      ownerId: ALICE_UID,
      restaurantId: REST_ID,
      nameAr: "شاورما عربي دجاج",
      nameEn: "Chicken Arabi Shawarma",
      categoryAr: "الشاورما",
      categoryEn: "Shawarma",
      descriptionAr: "تقدم مع ثومية وبطاطس",
      descriptionEn: "Served with garlic sauce and fries",
      price: 165.0,
      ingredients: ["دجاج متبل", "خبز صاج", "ثومية"],
      imageKey: "shawarma",
      available: true,
      featured: true,
      prepMinutes: 12,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    restRef.collection("menu_items").where("ownerId", "==", ALICE_UID).get()
  );
  await assertFails(restRef.collection("menu_items").get());

  // Order lifecycle & terminal state lock
  await assertSucceeds(
    restRef.collection("orders").doc("ord_1").set({
      ownerId: ALICE_UID,
      restaurantId: REST_ID,
      orderNumber: "GHZ-1001",
      customerName: "أحمد محمود",
      customerPhone: "01055544332",
      orderType: "DELIVERY",
      paymentMethod: "INSTAPAY",
      status: "NEW",
      itemsSummary: "2x شاورما عربي دجاج",
      itemCount: 2,
      totalAmount: 330.0,
      notes: "بدون مخلل",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    restRef.collection("orders").doc("ord_1").update({
      status: "COMPLETED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Owner cannot mutate COMPLETED order (terminal lock)
  await assertFails(
    restRef.collection("orders").doc("ord_1").update({
      status: "NEW",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Admin can override if needed
  await assertSucceeds(
    adminDb.collection("restaurants").doc(REST_ID).collection("orders").doc("ord_1").update({
      status: "PREPARING",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Inventory & Staff
  await assertSucceeds(
    restRef.collection("inventory").doc("inv_1").set({
      ownerId: ALICE_UID,
      restaurantId: REST_ID,
      nameAr: "صدور دجاج طازجة",
      nameEn: "Fresh Chicken Breast",
      unit: "kg",
      currentStock: 8.5,
      minThreshold: 10.0,
      unitCost: 190.0,
      supplierName: "المزارع المتحدة",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    restRef.collection("staff").doc("stf_1").set({
      ownerId: ALICE_UID,
      restaurantId: REST_ID,
      fullName: "كريم الكاشير",
      email: "karim@ghazalo.app",
      phone: "01022334455",
      role: "CASHIER",
      active: true,
      canEditMenu: false,
      canAccessCashier: true,
      canViewReports: false,
      canManageInventory: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Cross-user isolation on subcollections
  await assertFails(
    bobDb.collection("restaurants").doc(REST_ID).collection("orders").where("ownerId", "==", ALICE_UID).get()
  );
  await assertFails(
    bobDb.collection("restaurants").doc(REST_ID).collection("inventory").where("ownerId", "==", ALICE_UID).get()
  );
});
