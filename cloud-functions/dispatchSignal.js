/**
 * Sample callable function to relay encrypted signal via FCM data message.
 * Deploy with: firebase deploy --only functions:dispatchSignal
 */
const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

exports.dispatchSignal = functions.https.onCall(async (data, context) => {
  const { signalId, coupleId, encryptedEmotionId } = data;
  if (!signalId || !coupleId || !encryptedEmotionId) {
    throw new functions.https.HttpsError("invalid-argument", "Missing payload");
  }

  const db = admin.firestore();
  const usersSnap = await db.collection("users").where("coupleId", "==", coupleId).get();
  const tokens = [];
  usersSnap.forEach((doc) => {
    const token = doc.get("fcmToken");
    if (token) tokens.push(token);
  });

  if (tokens.length === 0) {
    return { status: "no_tokens" };
  }

  const message = {
    data: { signalId, coupleId, encryptedEmotionId },
    tokens,
    android: {
      priority: "high",
      notification: {
        channelId: "pulse_signal_channel",
        sound: null,
        notificationCount: 0,
      },
    },
    apns: {
      headers: { "apns-priority": "10" },
      payload: { aps: { "content-available": 1 } },
    },
  };

  await admin.messaging().sendEachForMulticast(message);
  return { status: "sent", recipients: tokens.length };
});
