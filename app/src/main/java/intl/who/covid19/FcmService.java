package intl.who.covid19;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class FcmService extends FirebaseMessagingService {
    @Override
    public void onNewToken(@NonNull String s) {
        SharedPreferences prefs = App.get(this).prefs();
        prefs.edit().putString(Prefs.FCM_TOKEN, s).apply();
        // Check if we're agreed and registered and if so, send the token to API
        if (prefs.getBoolean(Prefs.TERMS, false)) {
            Api.ProfileRequest request = new Api.ProfileRequest(prefs.getString(Prefs.DEVICE_UID, null),
                    s, prefs.getString(Prefs.PHONE_NUMBER, null));
            new Api(this).createProfile(request, null);
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        // TODO LATER Implement
    }
}
