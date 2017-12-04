package org.twaindirect;

import org.json.JSONObject;
import org.twaindirect.session.AsyncResponse;
import org.twaindirect.session.AsyncResult;
import org.twaindirect.session.Session;
import org.twaindirect.session.SessionListener;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * TWAIN Direct Library Development Testing Class
 *
 *
 */

public class TwainDirect {
    Session session;
    private static final Logger logger = Logger.getLogger(TwainDirect.class.getName());

    public static void main(String[] args) {
        TwainDirect app = new TwainDirect();
        try {
            if (args.length == 0) {
                System.out.println("Scanner URL not provided.");
                System.exit(1);
            }

            app.run(args[0]);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void run(String urlString) throws MalformedURLException {
        logger.info("TwainDirect Test App Startup");

        URL url = new URL(urlString);
        System.out.println("Opening session to " + url);

        session = new Session(url, url.getHost());
        session.setTempDir(new File("/tmp"));

        session.setSessionListener(new SessionListener() {
            @Override
            public void onImageReceived(Session session, File pdfPath, JSONObject metadata) {
                logger.info(String.format("onImageReceived " + pdfPath.toString()));
            }

            @Override
            public void onStateChanged(Session session, Session.State oldState, Session.State newState) {
                logger.info(String.format("onStateChanged, old=%s, new=%s", oldState, newState));
            }

            @Override
            public void onStatusChanged(Session session, boolean success, Session.StatusDetected status) {
                logger.info(String.format("onStatusChanged, success=%s, status=%s", success, status));
            }

            @Override
            public void onDoneCapturing(Session session) {
                logger.info("onDoneCapturing");
                System.exit(0);
            }

            @Override
            public void onConnectionError(Session session, Exception e) {
                logger.severe("Error detected in session");
                if (e != null) {
                    logger.severe(e.toString());
                }
            }
        });


        session.open(new AsyncResponse() {
            @Override
            public void onSuccess() {
                System.out.println("Session open succeeded: " + session.toString());
                sendTask();
            }

            @Override
            public void onError(Exception e) {
                System.out.println("Session open failed:");
                e.printStackTrace();
            }
        });
    }

    // Close the test session and exit
    private void close() {
        session.close(new AsyncResponse() {
            @Override
            public void onSuccess() {
                System.out.println("Session closed. Exiting.");
                System.exit(0);
            }

            @Override
            public void onError(Exception e) {
                System.out.println("Exception closing session");
                e.printStackTrace();
            }
        });
    }

    private void sendTask() {
        // Let's send a task
        JSONObject task = new JSONObject("{\"actions\": [ { \"action\": \"configure\" } ] }");

        session.sendTask(task, new AsyncResult<JSONObject>() {
            @Override
            public void onResult(JSONObject result) {
                System.out.println("Success response from sending task: " + result.toString());

                // Let's move on to requesting an image
                startCapturing();
            }

            @Override
            public void onError(Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        });
    }

    private void startCapturing() {
        session.startCapturing(new AsyncResponse() {
            @Override
            public void onSuccess() {
                System.out.println("startCapturing succeeded .. waiting for pages");
            }

            @Override
            public void onError(Exception e) {
                System.out.println("startCapturing failed");
                e.printStackTrace();
            }
        });
    }
}
