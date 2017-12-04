package org.twaindirect.sample;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

/**
 * Show the contents of the TWAIN Direct sample images folder, and let the user pick
 * an image to launch it in the default PDF viewer.
 */
public class FileListActivity extends AppCompatActivity {

    private ListView listView;
    private String[] files;
    private ArrayAdapter<String> adapter;
    private FileObserver observer;
    private File imagesPath;
    private Button deleteAllButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_list);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        deleteAllButton = (Button)findViewById(R.id.delete_all);
        deleteAllButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File folder = new File(Environment.getExternalStorageDirectory(), getString(R.string.image_folder_name));
                String[] fileNames = folder.list();
                for (String fileName : fileNames) {
                    (new File(folder, fileName)).delete();
                }

                populate();
            }
        });

        imagesPath = new File(Environment.getExternalStorageDirectory(), getString(R.string.image_folder_name));
        observer = new FileObserver(imagesPath.getAbsolutePath(), FileObserver.CREATE|FileObserver.DELETE|FileObserver.MOVED_FROM|FileObserver.MOVED_TO) {
            @Override
            public void onEvent(int i, String s) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        populate();
                    }
                });
            }
        };

        listView = (ListView)findViewById(R.id.file_list);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                String fileName = files[i];

                File folder = new File(Environment.getExternalStorageDirectory(), getString(R.string.image_folder_name));
                File imageFile = new File(folder, fileName);

                Uri uri = Uri.parse("content://org.twaindirect.sample.provider/");
                uri = Uri.withAppendedPath(uri, fileName);

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, "application/pdf");
                startActivity(intent);
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();

        populate();
        observer.startWatching();
    }

    @Override
    protected void onPause() {
        super.onPause();
        observer.stopWatching();
    }

    private void populate() {
        files = imagesPath.list();
        Arrays.sort(files, Collections.reverseOrder());

        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, files);
        listView.setAdapter(adapter);

        deleteAllButton.setEnabled(files.length > 0);
    }
}
