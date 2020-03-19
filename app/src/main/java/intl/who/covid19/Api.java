package intl.who.covid19;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;

import sk.turn.http.Http;

public class Api {
    public interface Listener {
        void onResponse(int status, String response);
    }
    private static class Response {
        private int status;
        private String body;
        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
    public static class ProfileRequest {
        private String deviceId;
        private String pushToken;
        private String phoneNumber;
        private String locale;
        public ProfileRequest(String deviceUid, String pushToken, String phoneNumber) {
            deviceId = deviceUid;
            this.pushToken = pushToken;
            this.phoneNumber = phoneNumber;
            locale = java.util.Locale.getDefault().toString();
        }
    }
    public static class ProfileResponse {
        public long profileId;
        public String deviceId;
    }
    public static class ContactRequest {
        private final String sourceDeviceId;
        private final long sourceProfileId;
        public final ArrayList<Encounter> connections = new ArrayList<>();
        public ContactRequest(String sourceDeviceId, long sourceProfileId) {
            this.sourceDeviceId = sourceDeviceId;
            this.sourceProfileId = sourceProfileId;
        }
    }
    public static class AuthTokenRequest {
        private final String deviceId;
        private final long profileId;
        public AuthTokenRequest(String deviceId, long profileId) {
            this.deviceId = deviceId;
            this.profileId = profileId;
        }
    }
    public static class ConfirmQuarantineRequest {
        private final String deviceId;
        private final long profileId;
        private final String duration;
        private final String mfaToken;
        public ConfirmQuarantineRequest(String deviceId, long profileId, int duration, String mfaToken) {
            this.deviceId = deviceId;
            this.profileId = profileId;
            this.duration = String.valueOf(duration);
            this.mfaToken = mfaToken;
        }
    }
    public static class LocationRequest {
        private final String deviceId;
        private final long profileId;
        public final List<Location> locations = new ArrayList<>();
        public LocationRequest(String deviceId, long profileId) {
            this.deviceId = deviceId;
            this.profileId = profileId;
        }
    }
    public static class QuarantineLeftRequest {
        private final String deviceId;
        private final long profileId;
        private final double latitude;
        private final double longitude;
        private final int accuracy;
        private final long recordTimestamp;
        public QuarantineLeftRequest(String deviceId, long profileId, android.location.Location location) {
            this.deviceId = deviceId;
            this.profileId = profileId;
            latitude = location.getLatitude();
            longitude = location.getLongitude();
            accuracy = (int) location.getAccuracy();
            recordTimestamp = location.getTime() / 1000;
        }
    }
    public static class Stats {
        public static Stats fromJson(String json) {
            return new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create().fromJson(json, Stats.class);
        }

        public int activeCases;
        public int newCases;
        public int newDeaths;
        public int seriousCritical;
        public int topCases;
        public int totalCases;
        public int totalDeaths;
        public int totalRecovered;
        /** Internal field to keep the app from updating too often */
        public long lastUpdate;

        public String toJson() {
            return new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create().toJson(this);
        }
    }

    private final App app;
    public Api(Context context) {
        app = App.get(context);
    }

    public void createProfile(ProfileRequest request, Listener listener) {
        send("profile", Http.PUT, request, listener);
    }
    public void sendContacts(ContactRequest request, Listener listener) {
        send("profile/contacts", Http.POST, request, listener);
    }
    public void requestAuthToken(AuthTokenRequest request, Listener listener) {
        send("profile/mfatoken", Http.POST, request, listener);
    }
    public void confirmQuarantine(ConfirmQuarantineRequest request, Listener listener) {
        send("profile/quarantine", Http.POST, request, listener);
    }
    public void sendLocations(LocationRequest request, Listener listener) {
        send("profile/location", Http.POST, request, listener);
    }
    public void quarantineLeft(QuarantineLeftRequest request, Listener listener) {
        send("profile/areaexit", Http.POST, request, listener);
    }

    @SuppressLint("StaticFieldLeak")
    private void send(String action, String method, Object request, final Listener listener) {
        new AsyncTask<Void, Void, Response>() {
            @Override
            protected Response doInBackground(Void... voids) {
                try {
                    String data = new Gson().toJson(request);
                    App.log("API > " + method + " " + action + " " + data);
                    Uri uri = Uri.withAppendedPath(app.apiUri(), action);
                    Http http = new Http(uri.toString(), method)
                            .addHeader("Content-Type", "application/json")
                            .setData(data)
                            .send();
                    int code = http.getResponseCode();
                    String response = http.getResponseString();
                    App.log("API < " + code + " " + response + (code == 200 ? "" : " " + http.getResponseMessage()));
                    return new Response(http.getResponseCode(), response);
                } catch (Exception e) {
                    App.log("API failed " + e);
                    return new Response(-1, e.getMessage());
                }
            }
            @Override
            protected void onPostExecute(Response response) {
                if (listener != null) {
                    listener.onResponse(response.status, response.body);
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
