package org.twaindirect.discovery;

import java.util.List;

/**
 * Simple interface used by a scanner discoverer to notify that the list of discovered
 * scanners has changed.
 */

public interface ScannerDiscoveredListener {
    void onDiscoveredScannerListChanged(List<ScannerInfo> discoveredScanners);
}
