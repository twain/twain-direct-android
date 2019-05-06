package org.twaindirect.cloud;

/**
 * EventBroker info returned from the /user endpoint. This indicates the
 * method we need to use to get async responses and events - typically the type
 * is "mqtt", the URL is a WebSocket URL and the topic is the MQTT topic to subscribe to.
 */
public class CloudEventBrokerInfo {
    public String type;
    public String url;
    public String topic;
}
