package org.twaindirect.cloud;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.twaindirect.session.AsyncResponse;
import org.twaindirect.session.AsyncResult;
import org.twaindirect.session.Session;
import org.twaindirect.session.URIUtils;

import java.net.URI;

/**
 * A TWAIN Cloud client uses the CloudSession object to establish a connection to a
 * cloud scanner, and then uses the associated Session to scan images.
 *
 * To use:
 *  - Instantiate the CloudSession
 *  - call createSession
 *
 * This will request the scanner info and event broker info from the cloud,
 * and then prepare a Session. This process is async and the session will be
 * passed to the callback when it is ready.
 *
 * Once connected, the Session provides the same API as a local scanner.
 */
public class CloudSession {
    /**
     * The URL for the TWAIN Cloud service
     */
    private URI apiRoot;

    /**
     * The scanner we're working with.
     */
    private String scannerId;

    /**
     * Cloud API authorization token.
     */
    private String accessToken;

    /**
     * MQTT event listener
     */
    private CloudEventBroker cloudEventBroker;

    /**
     * CloudConnection, which manages and refreshes our access token
     */
    private CloudConnection cloudConnection;

    /**
     * Prepare the cloud session.
     * Pass in the authorization token.
     */
    public CloudSession(URI apiRoot, String scannerId, CloudConnection cloudConnection) {
        this.apiRoot = apiRoot;
        this.scannerId = scannerId;
        this.cloudConnection = cloudConnection;
    }

    /**
     * Create the session. Makes some cloud calls to learn how to
     * communicate with the scanner, prepares the session, and
     * then calls listener when the session is ready to use.
     * @param listener
     */
    public void createSession(final AsyncResult<Session> listener) {
        cloudConnection.getEventBrokerInfo(new AsyncResult<CloudEventBrokerInfo>() {
            @Override
            public void onResult(CloudEventBrokerInfo eventBrokerInfo) {
                try {
                    cloudEventBroker = new CloudEventBroker(cloudConnection.getAccessToken(), eventBrokerInfo);
                    cloudEventBroker.connect(new AsyncResponse() {
                        @Override
                        public void onSuccess() {
                            URI url = URIUtils.appendPathToURI(apiRoot, "/scanners/" + scannerId);
                            Session session = new Session(url, cloudEventBroker, cloudConnection);
                            listener.onResult(session);
                        }

                        @Override
                        public void onError(Exception e) {
                            listener.onError(e);
                        }
                    });
                } catch (MqttException e) {
                    listener.onError(e);
                    return;
                }
            }

            @Override
            public void onError(Exception e) {
                listener.onError(e);
            }
        });
    }
}
