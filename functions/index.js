const hmac_sha256 = require('crypto-js/hmac-sha256');
const request = require('request');

const functions = require('firebase-functions');

const serviceAccount = require('./service-account-key.json');
const admin = require('firebase-admin');
const firebaseConfig = JSON.parse(process.env.FIREBASE_CONFIG);
firebaseConfig.credential = admin.credential.cert(serviceAccount);
admin.initializeApp(firebaseConfig);

exports.getCustomToken = functions.https.onRequest((req, res) => {
    const number = req.query.number;

    admin.auth().createCustomToken(number)
                    .then(customToken => res.status(200).send(customToken))
                    .catch(error => {
                        console.error('Creating custom token failed:', error);
                        res.status(400).send(error);
                    })
});

exports.deleteMessage = functions.https.onRequest((req, res) => {

  const roomId = req.query.roomid,
        messageId = req.query.messageid;

  var db = admin.database();
  var messageRef = db.ref('message/' + roomId + "/" + messageId);

  return messageRef.once("value").then((snapshot) => {
    var timeout = snapshot.val().lifeTime;

    setTimeout(function() {
        messageRef.remove();
    }, timeout * 1000);

    return timeout;

  }, (errorObject) => {
    console.log("The read failed: " + errorObject.code);
    throw new Error("The read failed: " + errorObject.code)
  });

//  messageRef.remove();
});