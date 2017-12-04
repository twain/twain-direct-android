package org.twaindirect.sample;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;

import org.json.JSONException;
import org.json.JSONObject;
import org.twaindirect.discovery.AndroidServiceDiscoverer;
import org.twaindirect.discovery.ScannerDiscoveredListener;
import org.twaindirect.discovery.ScannerInfo;
import org.twaindirect.session.AsyncResponse;
import org.twaindirect.session.AsyncResult;
import org.twaindirect.session.Session;
import org.twaindirect.session.SessionListener;
import org.twaindirect.session.StreamUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.fabric.sdk.android.Fabric;

import static android.support.design.widget.Snackbar.LENGTH_INDEFINITE;
import static org.twaindirect.session.Session.State;

/**
 * MainActivity
 *
 * This is the main UI for the TWAIN Direct sample.
 *
 * It provides a simple UI to drive a TWAIN Direct Session, letting the
 * callbacks drive the state of the UI.
 *
 * The user selects a scanner, selects a task, and then can start a scan.
 * Progress is reported based on the onStatusChanged and onStateChanged
 * methods of SessionListener.
 */

public class MainActivity extends AppCompatActivity implements SessionListener {
    private static final Logger logger = Logger.getLogger(Session.class.getName());

    private static final String TAG = "MainActivity";

    private static final int PICK_TASK_REQUEST = 1;
    private static final int PERMISSION_REQUEST = 1;

    /**
     * TWAIN Direct session object - this is our connection to the scanner.
     */
    private Session session;
    File tempDir;
    private String lastImageNameReceived;

    private AndroidServiceDiscoverer serviceDiscoverer;
    private boolean scannerOnline;

    /**
     * Widgets
     */
    private Button playButton;
    private Button pauseButton;
    private Button stopButton;
    private TextView currentStateTextView;
    private Snackbar snackbar;

    /**
     * Start of our life cycle - setup, add listeners.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AndroidLoggingHandler.reset(new AndroidLoggingHandler());

        java.util.logging.Logger.getLogger("org.twaindirect.session.Session").setLevel(Level.ALL);
        java.util.logging.Logger.getLogger("org.twaindirect.session.BlockDownloader").setLevel(Level.ALL);
        java.util.logging.Logger.getLogger("org.twaindirect.session.HttpJsonRequest").setLevel(Level.ALL);
        java.util.logging.Logger.getLogger("org.twaindirect.session.HttpBlockRequest").setLevel(Level.ALL);

        logger.info("Startup");

        Fabric.with(this, new Crashlytics());

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Handle click on the scanner name
        TextView tv = (TextView)findViewById(R.id.scanner_name);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, ScannerPickerActivity.class);
                startActivity(intent);
            }
        });

        // Handle click on the link to the task name
        tv = (TextView)findViewById(R.id.task_name);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");

                String chooserTitle = getString(R.string.select_task_chooser_title);
                startActivityForResult(Intent.createChooser(intent, chooserTitle), PICK_TASK_REQUEST);
            }
        });

        // Handle click on the link to the task builder
        tv = (TextView)findViewById(R.id.launch_task_generator);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String url = getString(R.string.task_generator_url);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
            }
        });

        playButton = (Button)findViewById(R.id.play_button);
        pauseButton = (Button)findViewById(R.id.pause_button);
        stopButton = (Button)findViewById(R.id.stop_button);
        currentStateTextView = (TextView)findViewById(R.id.current_state);

        // Play button click
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playButton.setEnabled(false);

                if (session == null) {
                    startSession();

                } else {
                    startCapturing();
                }
            }
        });

        // Pause button click
        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pauseButton.setEnabled(false);
                stopCapturing();
            }
        });

        // Stop button click
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopButton.setEnabled(false);
                closeSession();
            }
        });

        findViewById(R.id.show_files_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File path = new File(Environment.getExternalStorageDirectory(), getString(R.string.image_folder_name));
                if (!path.exists()) {
                    path.mkdir();
                }

                Intent intent = new Intent(MainActivity.this, FileListActivity.class);
                startActivity(intent);
            }
        });

        updateButtons();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Continue on if we still have the session, or start a new one.
                if (session != null) {
                    startCapturing();
                } else {
                    startSession();
                }
            }
        }
    }

    /**
     * Helper to show an exception's message.
     * @param title
     * @param e exception whose getLocalizedMessage to use as the body of the alert
     */
    private void reportException(final String title, final Exception e) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) {
                    String body = "";
                    if (e != null) {
                        body = e.getLocalizedMessage();
                        if (body == null) {
                            body = e.toString();
                        }
                    }

                    new AlertDialog.Builder(MainActivity.this).setTitle(title).setMessage(body).setPositiveButton(R.string.ok, null).show();
                }
            }
        });
    }

    /**
     * Update the UI to indicate the selected scanner and task.
     */
    private void updateLabels() {
        // Update the scanner label
        SharedPreferences prefs = Preferences.getSharedPreferences(this);
        String scannerLabel = getString(R.string.no_scanner_selected_tap_to_select);

        String selectedScannerJSON = prefs.getString("selectedScanner", null);
        if (selectedScannerJSON != null) {
            ScannerInfo scannerInfo = ScannerInfo.fromJSON(selectedScannerJSON);
            if (scannerInfo != null) {
                scannerLabel = scannerInfo.getFriendlyName();

                if (!scannerOnline) {
                    scannerLabel += " (Offline)";
                }
            }
        }

        TextView tv = (TextView)findViewById(R.id.scanner_name);
        tv.setText(scannerLabel);

        // Update the selected task label
        String selectedTaskName = prefs.getString("selectedTaskName", null);
        String taskLabel = getString(R.string.no_task_selected_tap_to_select);
        if (selectedTaskName != null) {
            taskLabel = selectedTaskName;
        }

        tv = (TextView)findViewById(R.id.task_name);
        tv.setText(taskLabel);

        // Update the session label
        tv = (TextView)findViewById(R.id.current_state);
        if (session == null) {
            tv.setText(R.string.no_session);
        } else {
            tv.setText(session.getState().toString());
        }
    }

    /**
     * Update the label showing the current state.
     */
    private void updateStatusLabel() {
        State state = State.noSession;

        if (session != null) {
            state = session.getState();
        }

        String statusLabel = state.toString();

        if (lastImageNameReceived != null) {
            statusLabel += "\n" + String.format(getString(R.string.saved_name), lastImageNameReceived);
        }

        currentStateTextView.setText(statusLabel);
    }

    /**
     * Set the enabled state of the play/pause/stop buttons to match the current session state.
     */
    private void updateButtons() {
        boolean enablePlay = false;
        boolean enablePause = false;
        boolean enableStop = false;

        if (session == null) {
            // No session .. enable the Play button if there is a scanner and task configured
            SharedPreferences prefs = Preferences.getSharedPreferences(this);
            String selectedScannerJSON = prefs.getString("selectedScanner", null);
            if (selectedScannerJSON != null) {
                ScannerInfo scannerInfo = null;
                scannerInfo = ScannerInfo.fromJSON(selectedScannerJSON);
                if (scannerInfo != null && scannerOnline) {
                    // We have a scanner .. do we have a task?
                    if (prefs.getString("selectedTaskJSON", null) != null) {
                        enablePlay = true;
                    }
                }
            }
        } else {
            // There is a session .. buttons state depends on session state
            switch (session.getState()) {
                case noSession:
                    enablePlay = true;
                    break;
                case ready:
                    enablePlay = true;
                    enableStop = true;
                    break;
                case capturing:
                    enablePause = true;
                    enableStop = true;
                    break;
                case draining:
                    // No buttons enabled
                    enableStop = true;
                    break;
                case closed:
                    // No buttons enabled
                    break;
            }
        }

        playButton.setEnabled(enablePlay);
        pauseButton.setEnabled(enablePause);
        stopButton.setEnabled(enableStop);
    }

    private void startSession() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST);
                return;
            }
        }

        // No session yet - Start a session
        SharedPreferences prefs = Preferences.getSharedPreferences(this);
        URL url;
        String selectedScannerJSON = prefs.getString("selectedScanner", null);
        ScannerInfo scannerInfo = ScannerInfo.fromJSON(selectedScannerJSON);
        url = scannerInfo.getUrl();

        String scannerIpAddr = scannerInfo.getIpAddr();

        session = new Session(url, scannerIpAddr);
        session.setTempDir(getCacheDir());
        session.setSessionListener(MainActivity.this);
        session.open(new AsyncResponse() {
            @Override
            public void onSuccess() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateLabels();
                        updateButtons();
                    }
                });
                sendTask();
            }

            @Override
            public void onError(Exception e) {
                reportException("Error creating session", e);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        resetSession();
                    }
                });
            }
        });
    }

    /**
     * Send the task configuration to the scanner.
     */
    private void sendTask() {
        String taskJSON = Preferences.getSharedPreferences(this).getString("selectedTaskJSON", null);
        JSONObject task;

        try {
            task = new JSONObject(taskJSON);
        } catch (JSONException e) {
            // This shouldn't be possible, since we validated that it's parseable before storing it.
            reportException("Selected task is corrupt", e);
            return;
        }

        session.sendTask(task, new AsyncResult<JSONObject>() {
            @Override
            public void onResult(JSONObject result) {
                startCapturing();
            }

            @Override
            public void onError(Exception e) {
                reportException("sendTask failed", e);
                resetSession();
            }
        });
    }

    /**
     * Ask the scanner to start capturing.
     */
    private void startCapturing() {
        lastImageNameReceived = null;

        session.startCapturing(new AsyncResponse() {
            @Override
            public void onSuccess() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateButtons();
                    }
                });
            }

            @Override
            public void onError(final Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        reportException("Error in startCapturing", e);
                        resetSession();
                    }
                });
            }
        });
    }

    /**
     * Ask the scanner to stop capturing.
     */
    private void stopCapturing() {
        session.stop(new AsyncResponse() {
            @Override
            public void onSuccess() {
                updateButtons();
            }

            @Override
            public void onError(final Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        reportException("Error stopping", e);
                        resetSession();
                    }
                });
            }
        });
    }

    private void closeSession() {
        session.close(new AsyncResponse() {
            @Override
            public void onSuccess() {
                // We've sent the closeSession request, but
                // the session is still active until the any
                // scanned images the scanner still has have
                // been delivered. We won't reset the session
                // until we receive the onDoneCapturing notification.
            }

            @Override
            public void onError(final Exception e) {
                // An error occurred closing the session. Reset it
                // here rather than waiting for the scanner to drain.
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        reportException("Error closing session", e);
                        session = null;
                        resetSession();
                    }
                });
            }
        });
    }

    /**
     * Closes the current session, if open, and resets local state.
     * Bounces to the main thread for convenience calling from error handlers.
     */
    private void resetSession() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // If the session exists, we should try to close it cleanly.
                if (session != null && session.getState() != State.closed && session.getState() != State.noSession) {
                    closeSession();
                } else {
                    if (snackbar != null) {
                        snackbar.dismiss();
                        snackbar = null;
                    }
                    session = null;
                    updateStatusLabel();
                    updateButtons();
                }
            }
        });
    }

    /**
     * Get the name of the file the user selected.
     */
    private String queryName(ContentResolver resolver, Uri uri) {
        Cursor returnCursor =
                resolver.query(uri, null, null, null, null);
        assert returnCursor != null;
        int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        returnCursor.moveToFirst();
        String name = returnCursor.getString(nameIndex);
        returnCursor.close();
        return name;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == PICK_TASK_REQUEST) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                Uri uri = data.getData();

                // Get the name of the file the user selected, if possible
                String fileName = uri.getLastPathSegment();
                Cursor returnCursor = getContentResolver().query(uri, null, null, null, null);
                if (returnCursor != null) {
                    int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    returnCursor.moveToFirst();
                    fileName = returnCursor.getString(nameIndex);
                    returnCursor.close();
                }

                try {
                    // Read the file content into a String
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    String jsonText = StreamUtils.inputStreamToString(inputStream);

                    // Try and parse it
                    try {
                        JSONObject json = new JSONObject(jsonText);

                        // It parsed - we don't do any further validation here
                        SharedPreferences prefs = Preferences.getSharedPreferences(this);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("selectedTaskName", fileName);
                        editor.putString("selectedTaskJSON", jsonText);
                        editor.apply();

                        updateLabels();
                        updateButtons();
                    } catch (JSONException e) {
                        // Not parseable - tell the user
                        new AlertDialog.Builder(this).setTitle("Not a valid task").setMessage(getString(R.string.invalid_json_colon) + e.getLocalizedMessage()).setPositiveButton(R.string.ok, null).show();
                    }

                } catch (FileNotFoundException e) {
                    new AlertDialog.Builder(this).setTitle(R.string.unable_to_open_file).setMessage(e.getLocalizedMessage()).setPositiveButton(R.string.ok, null).show();
                } catch (IOException e) {
                    new AlertDialog.Builder(this).setTitle(R.string.unable_to_open_file).setMessage(e.getLocalizedMessage()).setPositiveButton(R.string.ok, null).show();
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Android notification - we're about to be displayed.
     */
    @Override
    protected void onResume() {
        super.onResume();

        // Start discovery - we use this to update the label to indicate the scanner is online
        scannerOnline = false;
        serviceDiscoverer = new AndroidServiceDiscoverer(this, new ScannerDiscoveredListener() {
            @Override
            public void onDiscoveredScannerListChanged(final List<ScannerInfo> discoveredScanners) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        scannerOnline = false;

                        // Get the currently selected scanner
                        SharedPreferences prefs = Preferences.getSharedPreferences(MainActivity.this);
                        String selectedScannerJSON = prefs.getString("selectedScanner", null);
                        if (selectedScannerJSON != null) {
                            ScannerInfo scannerInfo = ScannerInfo.fromJSON(selectedScannerJSON);
                            if (scannerInfo != null) {
                                // We have a selected scanner .. see if it matches any of the
                                // discovered scanners
                                for (ScannerInfo discoveredScanner : discoveredScanners) {
                                    if (discoveredScanner.getUrl().equals(scannerInfo.getUrl())) {
                                        if (discoveredScanner.getFriendlyName().equals(scannerInfo.getFriendlyName())) {
                                            // Found it, it's online.
                                            scannerOnline = true;
                                            break;
                                        }
                                    }
                                }
                            }
                        }

                        updateLabels();
                        updateButtons();
                    }
                });
            }
        });
        serviceDiscoverer.start();

        updateLabels();
        updateButtons();
    }

    /**
     * We're just been removed from view.
     */
    @Override
    protected void onPause() {
        super.onPause();

        serviceDiscoverer.stop();
    }

    /**
     * We've received an image. We need to move or copy the file, because the file
     * at pdfPath will be deleted when this call returns.
     *
     * @param session
     * @param pdfPath
     * @param metadata
     */
    @Override
    public void onImageReceived(Session session, File pdfPath, JSONObject metadata) {
        Log.i(TAG, "onImageReceived: " + pdfPath.getName());
        File path = new File(Environment.getExternalStorageDirectory(), getString(R.string.image_folder_name));
        path.mkdir();

        // Generate a filename based on the current date/time, and the filename of the PDF (which
        // will be the sheetNumber-imageNumber.pdf).
        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
        final File dst = new File(path, dateFormat.format(date) + "-" + pdfPath.getName());

        // Copy the file to the destination
        FileChannel inChannel = null;
        try {
            inChannel = new FileInputStream(pdfPath).getChannel();
            FileChannel outChannel = new FileOutputStream(dst).getChannel();
            try {
                inChannel.transferTo(0, inChannel.size(), outChannel);
            } finally {
                if (inChannel != null)
                    inChannel.close();
                if (outChannel != null)
                    outChannel.close();

            }
        } catch (IOException e) {
            reportException("Unable to move image", e);
        }

        // Update the media scanner so the file shows up
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                intent.setData(Uri.fromFile(dst));
                sendBroadcast(intent);

                lastImageNameReceived = dst.getName();

                updateStatusLabel();
            }
        });
    }

    /**
     * Notification that images are drained and capturing is done.
     * @param session
     */
    @Override
    public void onDoneCapturing(Session session) {
        Log.i(TAG, "onDoneCapturing");
        resetSession();
    }

    /**
     * Unable to establish a waitForEvents listener
     * @param session
     */
    @Override
    public void onConnectionError(Session session, Exception e) {
        // This will close the session if it's open
        reportException("waitForEvents unable to connect", e);
        resetSession();
    }

    /**
     * Notification of state change - either as a result of a direct call result, or
     * an asynchronous event from the scanner.
     * @param session
     * @param oldState
     */
    @Override
    public void onStateChanged(final Session session, final State oldState, final State newState) {
        Log.i(TAG, "onStateChanged, new state=" + newState.toString());
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (newState == State.noSession) {
                    // Clear this so we create a new one the next time we start
                    MainActivity.this.session = null;
                    resetSession();
                }

                updateStatusLabel();
                updateButtons();
            }
        });
    }

    /**
     * Notification from the scanner that the status has changed.
     * @param session
     * @param success
     * @param status
     */
    @Override
    public void onStatusChanged(Session session, final boolean success, final Session.StatusDetected status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (success) {
                    if (MainActivity.this.snackbar != null) {
                        snackbar.dismiss();
                        snackbar = null;
                    }
                } else {
                    String message = String.format(getString(R.string.detected_status), status);
                    snackbar = Snackbar.make(playButton, message, LENGTH_INDEFINITE);

                    snackbar.setAction(R.string.dismiss, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            snackbar.dismiss();
                        }
                    });

                    snackbar.show();
                }
            }
        });
    }
}
