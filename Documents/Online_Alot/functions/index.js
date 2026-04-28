const { onCall, HttpsError } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();

const COL = {
  STAFF_ROLES: "staff_roles",
  ADMIN_ACCOUNTS: "admin_accounts",
  BARBERS: "barbers",
  ADMIN_INVITES: "admin_invites",
  AUDIT_LOGS: "audit_logs"
};

function normalizeEmail(email) {
  return String(email || "").trim().toLowerCase();
}

async function callerRole(auth) {
  if (!auth || !auth.token || !auth.token.email) return "";
  const email = normalizeEmail(auth.token.email);
  const doc = await db.collection(COL.STAFF_ROLES).doc(email).get();
  if (!doc.exists) return "";
  return String(doc.get("role") || "").trim().toLowerCase();
}

async function assertSuperAdmin(auth) {
  if (!auth) throw new HttpsError("unauthenticated", "Please sign in.");
  const role = await callerRole(auth);
  if (role !== "superadmin") {
    throw new HttpsError("permission-denied", "Super Admin access required.");
  }
}

async function writeAudit(action, byEmail, payload = {}) {
  await db.collection(COL.AUDIT_LOGS).add({
    action,
    by: normalizeEmail(byEmail),
    payload,
    ts: admin.firestore.FieldValue.serverTimestamp()
  });
}

exports.deleteBarberStaffAccount = onCall(async (req) => {
  await assertSuperAdmin(req.auth);
  const email = normalizeEmail(req.data && req.data.email);
  if (!email) {
    throw new HttpsError("invalid-argument", "Email is required.");
  }

  await db.collection(COL.ADMIN_ACCOUNTS).doc(email).delete();
  await db.collection(COL.BARBERS).doc(email).delete();
  await writeAudit("delete_staff_account", req.auth.token.email, { email });

  logger.info("deleteBarberStaffAccount", { email, by: req.auth.token.email });
  return { ok: true };
});

exports.upsertBarberProfile = onCall(async (req) => {
  if (!req.auth || !req.auth.token || !req.auth.token.email) {
    throw new HttpsError("unauthenticated", "Please sign in.");
  }
  const caller = normalizeEmail(req.auth.token.email);
  const role = await callerRole(req.auth);
  const email = normalizeEmail(req.data && req.data.email);
  const displayName = String((req.data && req.data.displayName) || "").trim();
  const availabilityHours = String((req.data && req.data.availabilityHours) || "").trim();
  const availabilityDays = String((req.data && req.data.availabilityDays) || "").trim();
  const photoUrl = String((req.data && req.data.photoUrl) || "").trim();

  if (!email) throw new HttpsError("invalid-argument", "Email is required.");
  if (role !== "superadmin" && caller !== email) {
    throw new HttpsError("permission-denied", "Not allowed to edit this profile.");
  }

  await db.collection(COL.BARBERS).doc(email).set({
    displayName,
    availabilityHours,
    availabilityDays,
    photoUrl,
    updatedAt: Date.now()
  }, { merge: true });

  await writeAudit("upsert_barber_profile", req.auth.token.email, { email });
  return { ok: true };
});

exports.resetBarberPassword = onCall(async (req) => {
  await assertSuperAdmin(req.auth);
  const email = normalizeEmail(req.data && req.data.email);
  const password = String((req.data && req.data.password) || "").trim();
  if (!email || password.length < 6) {
    throw new HttpsError("invalid-argument", "Valid email and password(min 6) required.");
  }
  await db.collection(COL.ADMIN_ACCOUNTS).doc(email).set({
    password,
    viaInvite: true,
    updatedAt: Date.now()
  }, { merge: true });
  await writeAudit("reset_barber_password", req.auth.token.email, { email });
  return { ok: true };
});
