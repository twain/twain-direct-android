package org.twaindirect.discovery;

import android.content.Context;

import com.youview.tinydnssd.DiscoverResolver;
import com.youview.tinydnssd.DiscoverResolver.Listener;
import com.youview.tinydnssd.MDNSDiscover;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**/

/**
 * AndroidServiceDiscoverer
 * Discovers TWAIN Direct scanners on the local network.
 * This version is specifically for Android.
 *
 * Uses the tinydnssd library for service discovery.
 * https://github.com/youviewtv/tinydnssd
 *
 * Android has NsdManager for service discovery, but until Android N, was unable
 * to return the TXT record from the discovered services. See the tinydnssd
 * README for a description of the problem and a link to the related Android bug.
 */

public class AndroidServiceDiscoverer {
    private static final Logger logger = Logger.getLogger(AndroidServiceDiscoverer.class.getName());

    private DiscoverResolver resolver;
    private List<ScannerInfo> discoveredScanners = new ArrayList<ScannerInfo>();
    private static final String TAG = "AndroidServiceDiscoverer";

    /**
     * Add the information about a service we discovered, and call the listener
     * to notify that the list has changed.
     * @param result
     */
    private ScannerInfo scannerInfoFromService(MDNSDiscover.Result result) {
        String host = result.a.ipaddr;
        int port = result.srv.port;
        String https = result.txt.dict.get("https");
        String protocol = "http";
        String fqdn = result.a.fqdn;
        if (https != null && Integer.parseInt(https) != 0) {
            protocol = "https";
        }

        // Construct the URL to the scanner's TWAIN Direct endpoint
        URL url = null;
        try {
            if (fqdn != null && !fqdn.isEmpty()) {
                url = new URL(protocol, fqdn, port, "/");
            } else {
                url = new URL(protocol, host, port, "/");
            }
            return new ScannerInfo(url, host, fqdn, result.txt.dict);
        } catch (MalformedURLException e) {
            // Ignore - invalid scanner, exclude from list
            logger.severe(e.toString());
            return null;
        }
    }
    /**
     * Instantiate the AndroidServiceDiscoverer, and start discovery.
     * @param context
     */
    public AndroidServiceDiscoverer(Context context, final ScannerDiscoveredListener discoveredListener) {
        Listener listener = new Listener() {
            @Override
            public void onServicesChanged(Map<String, MDNSDiscover.Result> services) {
                List<ScannerInfo> discoveredScanners = new ArrayList<ScannerInfo>();

                for (MDNSDiscover.Result result : services.values()) {
                    MDNSDiscover.TXT txt = result.txt;
                    if (txt != null) {
                        String type = txt.dict.get("type");
                        if (type.equals("twaindirect")) {
                            // Found one
                            ScannerInfo scannerInfo = scannerInfoFromService(result);
                            if (scannerInfo != null) {
                                discoveredScanners.add(scannerInfo);
                            }
                        }
                    }
                }

                discoveredListener.onDiscoveredScannerListChanged(discoveredScanners);
            }
        };

        resolver = new DiscoverResolver(context, "_privet._tcp", listener);
    }

    /**
     * Start discovery.
     */
    public void start() {
        // Start discovering
        resolver.start();
    }

    /**
     * Stop discovery. Note that there is no way to restart it for this instance.
     */
    public void stop() {
        resolver.stop();
    }

    /**
     * Get the current list of discovered scanners.
     */
    public List<ScannerInfo> getDiscoveredScanners() {
        return discoveredScanners;
    }
}
