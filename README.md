# Android application to help fight COVID-19

This app is aiming at helping fight COVID-19 spread by collecting anonymous data about people meeting each other.

In the basic scenario, the device is emitting an iBeacon signal (Bluetooth low energy) and at the same time listens to iBeacons around you. Thus creating an anonymous mesh of who met whom and when. This data is collected on server and when a person is positively diagnosed with SARS-CoV-2 (the infamous "corona" virus causing COVID-19 disease), the server will notify via push all the devices that were in a close and significant proximity with that person.

Alternatively, the user can flag himself as quarantined in which case the app will regularly check his/her GPS location and warn him/her in case he/she leaves the quarantine.

## Country-specific customisation steps
* Rename app/app.properties.example to app/app.properties and fill in your local-specific values
* Register your app in Google Firebase console and copy google-services.json file to app folder
* Create a flavor in app/build.gradle for your country
* Copy CountryDefaults.java from the global flavour into yours and implement your specifics
