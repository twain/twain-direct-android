package org.twaindirect.discovery;

import org.json.JSONException;
import org.json.JSONObject;
import org.twaindirect.cloud.CloudScannerInfo;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Wrapper for the information we have about a scanner, which is the URL, and
 * the mDNS TXT record we received during discovery. This is serialized to JSON
 * and saved to remember the selected scanner between sessions.
 */

public class ScannerInfo {
    private static final Logger logger = Logger.getLogger(ScannerInfo.class.getName());

    private static final String TAG = "ScannerInfo";

    public static final String TYPE_CLOUD = "cloud";
    public static final String TYPE_LOCAL = "local";

    // URL to the scanner's TWAIN Direct endpoint
    private URI url;

    // DNS name for the scanner - note that this may not resolve on Android because
    // it's like to be a .local address - but the url should resolve.
    private String fqdn;

    // TYPE_CLOUD or TYPE_LOCAL
    private String type;

    // IP address that fqdn resolves to, so we can do local resolution
    String ipaddr;

    // TWAIN Cloud Authorization Token
    private String cloudAccessToken;

    // TWAIN Cloud Refresh Token
    private String cloudRefreshToken;

    // TWAIN Cloud API base URL
    private URI cloudApiUrl;

    // TWAIN Cloud API selected scanner id
    private String cloudScannerId;

    // Textual name of the scanner shown in the UI
    private String name;

    // Note text for the scanner shown as a subtitle in the UI
    private String note;

    /**
     * Constructor for a local scanner
     */
    public ScannerInfo(URI url, String ipaddr, String fqdn, String name, String note) {
        this.url = url;
        this.ipaddr = ipaddr;
        this.fqdn = fqdn;
        this.type = TYPE_LOCAL;
        this.name = name;
        this.note = note;
    }

    /**
     * Constructor for a cloud scanner
     */
    public ScannerInfo(URI apiUrl, String authToken, String refreshToken, String id, String name, String note) {
        this.type = TYPE_CLOUD;
        this.cloudScannerId = id;
        this.cloudApiUrl = apiUrl;
        this.cloudAccessToken = authToken;
        this.cloudRefreshToken = refreshToken;
        this.name = name;
        this.note = note;
    }

    public URI getUrl() {
        return this.url;
    }

    public String getFqdn() {
        return this.fqdn;
    }

    public String getNote() {
        return note;
    }

    public String getType() {
        return type;
    }

    /**
     * Serialize to JSON. Used for storing in Preferences.
     * @return
     */
    public String toJSON() {
        JSONObject json = new JSONObject();
        try {
            if (fqdn != null) {
                json.put("fqdn", fqdn);
            }

            if (url != null) {
                json.put("url", url.toString());
            }

            if (ipaddr != null) {
                json.put("ipaddr", ipaddr);
            }

            if (cloudApiUrl != null) {
                json.put("cloudApiUrl", cloudApiUrl.toString());
            }

            if (cloudAccessToken != null) {
                json.put("cloudAccessToken", cloudAccessToken);
            }

            if (cloudRefreshToken != null) {
                json.put("cloudRefreshToken", cloudRefreshToken);
            }

            if (cloudScannerId != null) {
                json.put("cloudScannerId", cloudScannerId);
            }

            if (name != null) {
                json.put("name", name);
            }

            if (note != null) {
                json.put("note", note);
            }

            json.put("type", type);
        } catch (JSONException e) {
            // There should never be a JSONException serializing a ScannerInfo
            logger.severe(e.toString());
        }
        return json.toString();
    }

    /**
     * Instantiate from JSON. Used for restoring from Preferences.
     * @param jsonString as serialized by toJSON
     * @return ScannerInfo or null
     */
    public static ScannerInfo fromJSON(String jsonString) {
        JSONObject json = null;
        try {
            json = new JSONObject(jsonString);
            String type = json.getString("type");
            String name = json.getString("name");
            String note = json.getString("note");

            if (type.equals(TYPE_CLOUD)) {
                URI cloudApiUrl = new URI(json.getString("cloudApiUrl"));
                String cloudAccessToken = json.getString("cloudAccessToken");
                String cloudRefreshToken = json.getString("cloudRefreshToken");
                String cloudScannerId = json.getString("cloudScannerId");
                ScannerInfo scannerInfo = new ScannerInfo(cloudApiUrl, cloudAccessToken, cloudRefreshToken, cloudScannerId, name, note);
                return scannerInfo;
            } else {
                String urlString = json.getString("url");
                String fqdn = json.getString("fqdn");
                String ipaddr = json.getString("ipaddr");

                URI url = new URI(urlString);
                return new ScannerInfo(url, ipaddr, fqdn, name, note);
            }
        } catch (JSONException | URISyntaxException e) {
            logger.severe(e.toString());
            return null;
        }
    }

    /**
     * Return the IP address of the scanner. This may be null for a cloud scanner.
     * @return The IP address as reported by the scanner.
     */
    public String getIpAddr() {
        return ipaddr;
    }

    /**
     * Get the TWAIN Cloud's OAuth2 authorization token
     */
    public String getCloudAccessToken() {
        return cloudAccessToken;
    }

    /**
     * Get the TWAIN Cloud's OAuth2 refresh token
     */
    public String getCloudRefreshToken() {
        return cloudRefreshToken;
    }

    /**
     * Get the TWAIN Cloud's OAuth2 root API URL
     */
    public URI getCloudApiUrl() {
        return cloudApiUrl;
    }

    /**
     * Get the selected scanner's cloud scanner id
     */
    public String getCloudScannerId() {
        return cloudScannerId;
    }
}
