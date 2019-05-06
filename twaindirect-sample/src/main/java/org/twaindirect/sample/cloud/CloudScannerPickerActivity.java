package org.twaindirect.sample.cloud;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.twaindirect.cloud.CloudConnection;
import org.twaindirect.cloud.CloudScannerInfo;
import org.twaindirect.discovery.ScannerInfo;
import org.twaindirect.sample.Preferences;
import org.twaindirect.sample.R;
import org.twaindirect.sample.TwainDirectSampleApplication;
import org.twaindirect.session.AsyncResult;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class CloudScannerPickerActivity extends AppCompatActivity {
    private static final Logger logger = Logger.getLogger(CloudScannerPickerActivity.class.getName());

    ListView listView;
    private static final String TAG = "CloudScannerPickerAct";
    private List<CloudScannerInfo> scanners = new ArrayList<>();
    private ArrayAdapter<CloudScannerInfo> scannerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_cloud_scanner_picker);

        listView = (ListView)findViewById(R.id.scanner_list);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                CloudScannerInfo selectedScanner = scanners.get(i);

                TwainDirectSampleApplication application = (TwainDirectSampleApplication)getApplication();
                CloudConnection cloudConnection = application.cloudConnection;
                URI apiUrl = application.cloudConnection.getApiUrl();

                // Create a ScannerInfo to save the selected scanner
                ScannerInfo scannerInfo = new ScannerInfo(apiUrl, cloudConnection.getAccessToken(), cloudConnection.getRefreshToken(), selectedScanner.getScannerId(), selectedScanner.getName(), selectedScanner.getDescription());

                SharedPreferences prefs = Preferences.getSharedPreferences(CloudScannerPickerActivity.this);
                prefs.edit().putString("selectedScanner", scannerInfo.toJSON()).apply();

                finish();
            }
        });

        scannerAdapter = new CloudScannerInfoArrayAdapter(this, scanners);
        listView.setAdapter(scannerAdapter);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Snackbar.make(listView, R.string.searching_for_scanners, Snackbar.LENGTH_INDEFINITE).show();

        TwainDirectSampleApplication application = (TwainDirectSampleApplication)getApplication();
        application.cloudConnection.getScannerList(new AsyncResult<List<CloudScannerInfo>>() {
            @Override
            public void onResult(final List<CloudScannerInfo> scanners) {
                logger.info("Got scanner list");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        scannerAdapter.clear();
                        scannerAdapter.addAll(scanners);
                        scannerAdapter.notifyDataSetChanged();
                    }
                });

            }

            @Override
            public void onError(Exception e) {
                Log.e(CloudScannerPickerActivity.TAG, e.getMessage());
            }
        });
    }
}
