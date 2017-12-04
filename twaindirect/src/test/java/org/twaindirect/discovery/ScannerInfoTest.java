package org.twaindirect.discovery;

import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by stevex on 2017-09-08.
 */

public class ScannerInfoTest {
    @Test
    public void serializeDeserializeTest() throws MalformedURLException {
        // Set up a test object
        Map<String, String> testMap = new HashMap<String, String>();
        testMap.put("a", "one");
        testMap.put("b", "two");
        ScannerInfo si1 = new ScannerInfo(new URL("http://192.168.1.1/"), "192.168.1.1", "mydevice.local", testMap);

        assert(si1.getUrl().toString().equals("http://192.168.1.1/"));
        assert(si1.getFqdn().equals("mydevice.local"));

        // Serialize
        String json = si1.toJSON();

        // Deserialize
        ScannerInfo si2 = ScannerInfo.fromJSON(json);

        assert(si1.equals(si2));

        assert(si1.getFqdn().equals(si2.getFqdn()));
        assert(si1.getFriendlyName().equals(si2.getFqdn()));
        assert(si1.getNote() == null);
        assert(si2.getNote() == null);
        assert(si1.getUrl().equals(si2.getUrl()));
    }
}
