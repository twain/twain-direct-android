package org.twaindirect;

import org.json.JSONObject;
import org.twaindirect.cloud.CloudConnection;
import org.twaindirect.cloud.CloudSession;
import org.twaindirect.session.AsyncResponse;
import org.twaindirect.session.AsyncResult;
import org.twaindirect.session.Session;
import org.twaindirect.session.SessionListener;

import java.io.File;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * TWAIN Direct Library Development Testing Class
 *
 * Usage:
 *      TwainDirect local http://local-scanner.local:34034
 *      TwainDirect cloud http://my-twain-cloud.com/scanners/my-scanner-id auth-token
 *
 */
public class TwainDirect {
    CloudSession cloudSession;
    Session session;
    String authToken;

    private static final Logger logger = Logger.getLogger(TwainDirect.class.getName());

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                System.out.println("Usage:");
                System.out.println("TwainDirect local http://local-scanner.local:34034");
                System.out.println("TwainDirect cloud http://my-twain-cloud.com/scanners/my-scanner-id auth-token refresh-token");
                System.exit(1);
            }

            if (args[0].equals("cloud")) {
                // To create a session connected to a cloud scanner, we need to use
                // CloudSession to create the session for us.
                final TwainDirect app = new TwainDirect();
                URI apiRoot = new URI(args[1]);
                String scannerId = args[2];
                String authToken = args[3];
                String refreshToken = args[4];
                CloudConnection cloudConnection = new CloudConnection(apiRoot, authToken, refreshToken);
                CloudSession cloudSession = new CloudSession(apiRoot, scannerId, cloudConnection);
                cloudSession.createSession(new AsyncResult<Session>() {
                    @Override
                    public void onResult(Session session) {
                        System.out.println("Established cloud session");
                        app.runSession(session);
                    }

                    @Override
                    public void onError(Exception e) {
                        System.err.println(e.getMessage());
                    }
                });
            }

            if (args[0].equals("local")) {
                // Construct and run a local the session
                TwainDirect app = new TwainDirect();
                URI url = new URI(args[1]);
                System.out.println("Opening session to " + url);
                app.runSession(new Session(url, url.getHost()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Once a session is established, scanning works the same whether you're using a
     * local or a cloud scanner.
     * @param session The session to scan from
     */
    private void runSession(final Session session) {
        logger.info("Session Started");

        this.session = session;
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
//        JSONObject task = new JSONObject("{\"actions\": [ { \"action\": \"configure\" } ] }");

        // This task requests a B&W image
        JSONObject task = new JSONObject("{\"actions\":[{\"action\":\"configure\",\"streams\":[{\"sources\":[{\"source\":\"any\",\"pixelFormats\":[{\"pixelFormat\":\"bw1\",\"attributes\":[{\"attribute\":\"compression\",\"values\":[{\"value\":\"autoVersion1\"}]},{\"attribute\":\"numberOfSheets\",\"values\":[{\"value\":1}]}]}]}]}]}]}}");

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
