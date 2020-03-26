/*-
 * Copyright (c) 2020 Sygic a.s.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

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
	/** String (phone number verification code - may need to be sent later when confirming quarantine or disease) */
	public static final String PHONE_NUMBER_VERIFICATION_CODE = "phoneNumberVerificationCode";
	/** double (latitude of home address) */
	public static final String HOME_LAT = "homeLat";
	/** double (longitude of home address) */
	public static final String HOME_LNG = "homeLng";
	/** String (address of home) */
	public static final String HOME_ADDRESS = "homeAddress";
	/** long (date/time of when the quarantine ends in milliseconds) */
	public static final String QUARANTINE_ENDS = "quarantineEnds";
}
