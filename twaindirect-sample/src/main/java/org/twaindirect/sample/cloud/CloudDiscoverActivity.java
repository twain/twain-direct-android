package org.twaindirect.sample.cloud;

import android.app.ListActivity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.twaindirect.discovery.AndroidServiceDiscoverer;
import org.twaindirect.discovery.ScannerDiscoveredListener;
import org.twaindirect.discovery.ScannerInfo;
import org.twaindirect.sample.R;
import org.twaindirect.session.AsyncResult;
import org.twaindirect.session.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class CloudDiscoverActivity extends ListActivity implements ScannerDiscoveredListener {
    private static final Logger logger = Logger.getLogger(CloudDiscoverActivity.class.getName());

    public static final String CLOUD_URL_KEY = "cloudUrl";

    private AndroidServiceDiscoverer discoverer;
    private List<String> urls = new ArrayList<String>();
    private ArrayAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cloud_discover);

        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.cloud_discover);
        toolbar.setTitleTextColor(Color.WHITE);

        adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, urls);
        setListAdapter(adapter);

        discoverer = new AndroidServiceDiscoverer(this, this);
        discoverer.start();

        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                String url = urls.get(i);

                Intent data = new Intent();
                data.putExtra(CloudDiscoverActivity.CLOUD_URL_KEY, url);
                setResult(RESULT_OK, data);
                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        discoverer.stop();
        super.onDestroy();
    }

    @Override
    public void onDiscoveredScannerListChanged(List<ScannerInfo> discoveredScanners) {
        for (ScannerInfo scannerInfo : discoveredScanners) {
            Session session = new Session(scannerInfo.getUrl(), scannerInfo.getIpAddr());
            session.getInfoEx(new AsyncResult<JSONObject>() {
                @Override
                public void onResult(JSONObject result) {
                    try {
                        logger.info("Discovery response: " + result.toString(2));
                        if (result.has("clouds")) {
                            JSONArray clouds = result.getJSONArray("clouds");
                            for (int idx = 0; idx < clouds.length(); idx++) {
                                JSONObject cloud = clouds.getJSONObject(idx);
                                final String url = cloud.getString("url");
                                if (url != null && url.length() > 0) {
                                    if (!urls.contains(url)) {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                adapter.add(url);
                                            }
                                        });
                                    }
                                }
                            }
                        }
                    } catch (JSONException e) {
                        logger.warning(e.getMessage());
                    }
                }

                @Override
                public void onError(Exception e) {
                    logger.warning(e.getMessage());
                }
            });
        }
    }
}

