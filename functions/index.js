const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();

exports.sendCallNotification = onDocumentCreated("calls/{callId}", async (event) => {
    const snap = event.data;
    if (!snap) return;

    const call = snap.data();
    const callId = event.params.callId;

    if (!call || call.status !== "ringing") return;

    const calleeUid = call.calleeUid;
    if (!calleeUid) return;

    const userDoc = await admin.firestore().collection("users").doc(calleeUid).get();
    if (!userDoc.exists) return;

    const fcmToken = userDoc.data().fcmToken;
    if (!fcmToken) return;

    const message = {
        token: fcmToken,
        data: {
            type: "incoming_call",
            callId: callId,
            callerUid: call.callerUid || "",
            callerName: call.callerName || "Unknown",
            callerNumber: call.callerNumber || ""
        },
        android: {
            priority: "high"
        }
    };

    try {
        await admin.messaging().send(message);
    } catch (error) {
        console.error("FCM send failed:", error);
    }
});
