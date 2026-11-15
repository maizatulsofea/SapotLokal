/**
 * SapotLokal Cloud Functions (OPTIONAL — needs the Firebase Blaze plan).
 *
 * notifyShopOpened: when a vendor flips their shop OPEN or CLOSED, the app writes a row to
 * /shopNotifications. This function turns that row into a REAL push notification sent to the
 * FCM topic "shop_<vendorID>" — the topic every customer is subscribed to after their first
 * scan at that shop. Without this function nothing sends push messages (the app only shows an
 * in-app alert while the customer's app is open).
 *
 * Deploy:  firebase deploy --only functions
 */
const { onValueCreated } = require("firebase-functions/v2/database");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");

admin.initializeApp();

exports.notifyShopOpened = onValueCreated(
  {
    ref: "/shopNotifications/{notificationId}",
    instance: "sapotlokal-app-default-rtdb",
    region: "asia-southeast1",
  },
  async (event) => {
    const n = event.data.val();
    if (!n || !n.vendorID) return;

    const vendorName = n.vendorName || "A local shop";
    await admin.messaging().send({
      topic: `shop_${n.vendorID}`,
      notification: {
        title: n.title || (n.isOpen ? `${vendorName} is open` : `${vendorName} has closed`),
        body: n.body || (n.isOpen ? "Drop by today to earn points." : "We'll tell you when it opens again."),
      },
      data: { type: "shop_status", vendorId: String(n.vendorID), vendorName: String(vendorName) },
      android: { priority: "high", notification: { channelId: "channel_shops" } },
    });
    logger.info(`Sent shop-open push for ${n.vendorID}`);
  }
);
