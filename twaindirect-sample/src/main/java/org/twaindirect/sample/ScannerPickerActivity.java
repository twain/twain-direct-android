package org.twaindirect.sample;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.twaindirect.discovery.ScannerDiscoveredListener;
import org.twaindirect.discovery.ScannerInfo;
import org.twaindirect.discovery.AndroidServiceDiscoverer;

import java.util.ArrayList;
import java.util.List;

public class ScannerPickerActivity extends AppCompatActivity {

    ListView listView;
    private static final String TAG = "ScannerPickerActivity";
    private AndroidServiceDiscoverer serviceDiscoverer;
    private List<ScannerInfo> scanners = new ArrayList<ScannerInfo>();
    private ArrayAdapter<ScannerInfo> scannerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_scanner_picker);

        listView = (ListView)findViewById(R.id.scanner_list);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                ScannerInfo selectedScanner = scanners.get(i);

                SharedPreferences prefs = Preferences.getSharedPreferences(ScannerPickerActivity.this);
                prefs.edit().putString("selectedScanner", selectedScanner.toJSON()).apply();

                finish();
            }
        });

        scannerAdapter = new ScannerInfoArrayAdapter(this, scanners);
        listView.setAdapter(scannerAdapter);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Snackbar.make(listView, R.string.searching_for_scanners, Snackbar.LENGTH_INDEFINITE).show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (serviceDiscoverer != null) {
            serviceDiscoverer.stop();
        }

        serviceDiscoverer = new AndroidServiceDiscoverer(this, new ScannerDiscoveredListener() {
            @Override
            public void onDiscoveredScannerListChanged(final List<ScannerInfo> discoveredScanners) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        scannerAdapter.clear();
                        scannerAdapter.addAll(discoveredScanners);
                        scannerAdapter.notifyDataSetChanged();
                    }
                });
            }
        });

        serviceDiscoverer.start();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (serviceDiscoverer != null) {
            serviceDiscoverer.stop();
            serviceDiscoverer = null;
        }
    }
}
