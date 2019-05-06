package org.twaindirect.cloud;

/**
 * Listener interface used to deliver the result of CloudEventBroker.connect
 */
public interface CloudEventBrokerListener {
    // Return the outstanding command ID
    String getCommandId();

    // Received a JSON response
    void deliverJSONResponse(String body);
}

