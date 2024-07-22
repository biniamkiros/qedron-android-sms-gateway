# Qedron android sms gateway

Turn your phone into an sms gateway

<img src="https://github.com/user-attachments/assets/d9d1dd67-f670-4784-bb8e-f3c2d7394d7b" height="600">
    
<img src="https://github.com/user-attachments/assets/92c9e024-4210-4c74-a335-4acb5336b531" height="600">

<img src="https://github.com/user-attachments/assets/4f700979-0603-4e3b-af6b-0906a16b0219" height="600">

## Description

This is useful for developers for one reason or another, cannot use paid sms gateway services. This app will turn your phone into an sms gateway for your webapp.

## Getting Started

### Dependencies

- the app uses firebase cloud messaging to communicate with your server.
- You need to edit your device settings through adb to increase the max sms you can send in 30mins(default is 30). You can read how to do it [here](https://www.xda-developers.com/change-sms-limit-android/) and [here](https://www.xda-developers.com/install-adb-windows-macos-linux/)

### Installing

- You can fork and edit this app to fit your need or you can just download the apk and use it as is.
- You need to add fcm to your backend server by registering to firebase cloud messaging.


### Executing program

- add firebase admin to your server and initialise.

```js
const admin = require("firebase-admin");

admin.initializeApp({
  credential: admin.credential.cert({
    type: "service_account",
    project_id: env.FIREBASE_PROJECT_ID,
    private_key_id: env.FIREBASE_PRIVATE_KEY_ID,
    private_key: env.FIREBASE_PRIVATE_KEY,
    client_email: env.FIREBASE_CLIENT_EMAIL,
    client_id: env.FIREBASE_CLIENT_ID,
    auth_uri: "https://accounts.google.com/o/oauth2/auth",
    token_uri: "https://oauth2.googleapis.com/token",
    auth_provider_x509_cert_url: env.FIREBASE_CERT_URL,
    client_x509_cert_url: env.FIREBASE_CLIENT_CERT_URL,
  }),
  databaseURL: env.FIREBASE_DATABASE_URL,
});
```

- copy the token from the app and send message

```js
const fcmToken = env.TOKEN;
const payload = {
  data: {
    phone,
    message,
  },
};

const options = {
  priority: "high",
  timeToLive: 86400,
};

return admin
  .messaging()
  .sendToDevice(fcmToken, payload, options)
  .then((response) => {})
  .catch((error) => {});
```

<!-- ## Help

Any advise for common problems or issues.

```
command to run if program contains helper info
``` -->

## Authors

[@biniamkiross](https://x.com/biniamkiross)

## Version History

<!-- * 0.2
    * Various bug fixes and optimizations
    * See [commit change]() or See [release history]() -->

- 0.1
  - Initial Release

## License

    GNU General Public License, Version 3

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program. If not, see <http://www.gnu.org/licenses/>.

## Acknowledgments

- [traccar-sms-gateway](https://github.com/traccar/traccar-sms-gateway/blob/master/README.md)
