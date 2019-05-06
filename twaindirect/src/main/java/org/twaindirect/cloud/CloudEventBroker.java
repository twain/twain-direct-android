package org.twaindirect.cloud;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;
import org.twaindirect.session.AsyncResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Subscribe to the scanner's MQTT response topic and broker messages.
 * Commands register the command ID with CloudEventBroker, and responses are
 * dispatched as they arrive.
 */
public class CloudEventBroker {
    private static final Logger logger = Logger.getLogger(CloudEventBroker.class.getName());

    String authToken;
    CloudEventBrokerInfo eventBrokerInfo;
    MqttAsyncClient client;
    Map<String, MqttMessage> messagesReceived = new HashMap<>();

    /**
     * When we receive a message, we walk the listeners list looking for someone to deliver it to
     */
    List<CloudEventBrokerListener> listeners = new ArrayList<>();

    public CloudEventBroker(String authToken, CloudEventBrokerInfo eventBrokerInfo) throws MqttException {
        this.eventBrokerInfo = eventBrokerInfo;

        client = new MqttAsyncClient(eventBrokerInfo.url, MqttClient.generateClientId(), new MemoryPersistence());
        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                logger.warning("MQTT connection lost");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                logger.info("MQTT message arrived");
                String payloadJSON = new String(message.getPayload());
                JSONObject payload = new JSONObject(payloadJSON);
                logger.fine(payload.toString(2));

                // Message typically looks like this:
                // {
                //  "headers": {"content-Type": "application/json; charset=UTF-8"},
                //  "statusDescription": null,
                //  "requestId": null,
                //  "body": "{\"version\":\"1.0\",\"name\":\"TWAIN2 FreeImage Software Scanner\",\"description\":\"Sample DS\",\"url\":\"\",\"type\":\"twaindirect\",\"id\":\"\",\"device_state\":\"idle\",\"connection_state\":\"offline\",\"manufacturer\":\"TWAIN Working Group\",\"model\":\"TWAIN2 FreeImage Software Scanner\",\"serial_number\":\"X\",\"firmware\":\"2.1:1.2\",\"uptime\":\"1436\",\"setup_url\":\"\",\"support_url\":\"\",\"update_url\":\"\",\"x-privet-token\":\"50gbKrsF235rSr6RI58PSGghbpA=:636696641228998209\",\"api\":[\"/privet/twaindirect/session\"],\"semantic_state\":\"\",\"clouds\":[{\"url\":\"https://api-twain.hazybits.com/dev\",\"id\":\"3c807fab-07c2-4710-be56-5c6b40bedcaa\",\"connection_state\":\"online\",\"setup_url\":\"\",\"support_url\":\"\",\"update_url\":\"\"}]}",
                //  "statusCode": 200
                // }

                // Pick the right listener based on the command ID
                String bodyJSON = payload.getString("body");
                JSONObject body = new JSONObject(bodyJSON);
                logger.fine("Decoded message body: " + body.toString(2));
                String commandId = null;
                if (body.has("commandId")) {
                    commandId = body.getString("commandId");
                }
                CloudEventBrokerListener foundListener = null;
                for (CloudEventBrokerListener listener : listeners) {
                    String listenerCommandId = listener.getCommandId();
                    if ((listenerCommandId== null ? commandId == null : listenerCommandId.equals(commandId))) {
                        foundListener = listener;
                        break;
                    }
                }

                if (foundListener == null) {
                    logger.warning("Received command with no registered listener");
                } else {
                    removeListener(foundListener);
                }
                foundListener.deliverJSONResponse(bodyJSON);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // We don't send MQTT messages so we never receive deliveryComplete
                logger.info("deliveryComplete");
            }
        });
    }

    /**
     * Add a command listener.
     * @param listener
     */
    public void addListener(CloudEventBrokerListener listener) {
        synchronized(this) {
            listeners.add(listener);
        }
    }

    /**
     * Remove a command listener.
     * @param listener
     */
    private void removeListener(CloudEventBrokerListener listener) {
        synchronized(this) {
            listeners.remove(listener);
        }
    }

    /**
     * Connect to the MQTT endpoint asynchronously.
     * @param completion
     * @throws MqttException
     */
    public void connect(final AsyncResponse completion) throws MqttException {
        logger.fine("Connecting event broker to " + eventBrokerInfo.url);
        client.connect(null, new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                try {
                    logger.fine("Subscribing to " + eventBrokerInfo.topic);
                    client.subscribe(eventBrokerInfo.topic, 0);
                    completion.onSuccess();
                } catch (MqttException e) {
                    completion.onError(e);
                }
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable throwable) {
                completion.onError(new Exception(throwable));
            }
        });
    }
}
