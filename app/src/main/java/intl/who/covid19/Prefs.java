package intl.who.covid19;

public class Prefs {

	private static final int TERMS_VERSION = 1;

	/** boolean (whether terms are already agreed) */
	public static final String TERMS = "terms-v" + TERMS_VERSION;
	/** String (device-generated UUID) */
	public static final String DEVICE_UID = "deviceUid";
	/** long (server assigned sequential ID) */
	public static final String DEVICE_ID = "deviceId";
	/** String (current Firebase Cloud Messaging token) */
	public static final String FCM_TOKEN = "fcmToken";
	/** String (two-letter code of current country) */
	public static final String COUNTRY_CODE = "countryCode";
	/** String (confirmed phone number) */
	public static final String PHONE_NUMBER = "phoneNumber";
	/** double (latitude of home address) */
	public static final String HOME_LAT = "homeLat";
	/** double (longitude of home address) */
	public static final String HOME_LNG = "homeLng";
	/** String (address of home) */
	public static final String HOME_ADDRESS = "homeAddress";
	/** long (date/time of when the quarantine ends) */
	public static final String QUARANTINE_ENDS = "quarantineEnds";
	/** String (JSON object of statistics) */
	public static final String STATS = "stats";
}
