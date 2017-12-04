package org.twaindirect.discovery;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Wrapper for the information we have about a scanner, which is the URL, and
 * the mDNS TXT record we received during discovery.
 */

public class ScannerInfo {
    private static final Logger logger = Logger.getLogger(ScannerInfo.class.getName());

    private static final String TAG = "ScannerInfo";

    // URL to the scanner's TWAIN Direct endpoint
    private URL url;

    // DNS name for the scanner - note that this may not resolve on Android because
    // it's like to be a .local address - but the url should resolve.
    private String fqdn;

    // IP address that fqdn resolves to, so we can do local resolution
    String ipaddr;

    // TXT dictionary from the mDNS discovery
    private Map<String, String> txtDict;

    public ScannerInfo(URL url, String ipaddr, String fqdn, Map<String, String> txtDict) {
        this.url = url;
        this.ipaddr = ipaddr;
        this.txtDict = txtDict;
        this.fqdn = fqdn;
    }

    public URL getUrl() {
        return this.url;
    }

    public String getFqdn() {
        return this.fqdn;
    }

    public String getFriendlyName() {
        return txtDict.get("ty");
    }

    public String getNote() {
        return txtDict.get("note");
    }

    public String getType() {
        return txtDict.get("type");
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

            if (txtDict != null) {
                JSONObject mapObj = new JSONObject();
                for (String key : txtDict.keySet()) {
                    String value = txtDict.get(key);
                    mapObj.put(key, value);
                }
                json.put("txt", mapObj);
            }
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
            String urlString = json.getString("url");
            String fqdn = json.getString("fqdn");
            JSONObject map = json.getJSONObject("txt");
            String ipaddr = json.getString("ipaddr");

            // The txt dictionary is stored as a JSONObject - convert it back
            // into a Map<String, String>
            Map<String, String> txtDict = new HashMap<String, String>();
            Iterator<String> keys = map.keys();
            while (keys.hasNext()){
                String key = keys.next();
                txtDict.put(key, map.getString(key));
            }

            URL url = new URL(urlString);
            return new ScannerInfo(url, ipaddr, fqdn, txtDict);
        } catch (JSONException e) {
            logger.severe(e.toString());
            return null;
        } catch (MalformedURLException e) {
            logger.severe(e.toString());
            return null;
        }
    }

    /**
     * Return the IP address of the scanner
     * @return The IP address as reported by the scanner.
     */
    public String getIpAddr() {
        return ipaddr;
    }
}
