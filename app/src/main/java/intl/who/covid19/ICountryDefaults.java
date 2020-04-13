package intl.who.covid19;

import android.content.Context;

import java.util.List;

import intl.who.covid19.ui.HomeFragment;
import intl.who.covid19.ui.MapFragment;
import intl.who.covid19.ui.PhoneVerificationActivity;

public interface ICountryDefaults {
    /**
     * Whether to ask the user for his phone number and verify it at first launch of the app.
     */
    boolean verifyPhoneNumberAtStart();

    /**
     * Whether to allow the user to enter his start of the quarantine when entering quarantine.
     */
    boolean showQuarantineStartPicker();

    /**
     * Whether to use face ID when entering quarantine and later with random checks via a push message.
     * If you return true in your flavor, don't forget to include an implementation dependency in the module build.gradle
     */
    boolean useFaceId();

    /**
     * Two-letter code of current country (uppercase)
     */
    String getCountryCode();

    /**
     * Country center latitude.
     */
    double getCenterLat();

    /**
     * Country center longitude.
     */
    double getCenterLng();

    /**
     * Zoom to cover whole (most of the) country.
     */
    double getCenterZoom();

    /**
     * Load country-specific stats and return them in the callback. Be sure to call the callback on the main UI thread.
     * It is recommended to cache the stats locally in preferences and only reload them once a day/hour/etc.
     * @param context
     * @param callback
     */
    void getStats(Context context, App.Callback<HomeFragment.Stats> callback);

    /**
     * Load per-county stats for the country and return them in the callback. Be sure to call the callback on the main UI thread.
     * It is recommended to cache the stats locally in preferences and only reload them once a day/hour/etc.
     * @param context
     * @param callback
     */
    void getCountyStats(Context context, App.Callback<List<MapFragment.CountyStats>> callback);

    /**
     * Request a service to send a verification code to the specified phone number.
     * @param context
     * @param phoneNumber The phone number to send the code to in international format (E164).
     * @param callback Callback with optional exception, send with null for success.
     */
    void sendVerificationCodeText(Context context, String phoneNumber, App.Callback<Exception> callback);

    /**
     * The length of the phone number verification code.
     */
    int getVerificationCodeLength();

    /**
     *
     * @param context
     * @param code The code to verify with the server.
     * @param callback Callback with quarantine data, send with null for error.
     */
    void checkVerificationCode(Context context, String phoneNumber, String code, App.Callback<PhoneVerificationActivity.QuarantineDetails> callback);
}
