package org.twaindirect.session;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGetHC4;
import org.apache.http.client.methods.HttpPostHC4;
import org.apache.http.client.methods.HttpRequestBaseHC4;
import org.apache.http.entity.StringEntityHC4;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtilsHC4;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.twaindirect.cloud.CloudConnection;
import org.twaindirect.cloud.CloudEventBroker;
import org.twaindirect.cloud.CloudEventBrokerListener;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * Wrapper for a JSON request.
 * For TWAIN Cloud connections, responses to our JSON requests are delivered asynchronously
 * through the CloudEventBroker. This class is aware of this, and when we're in cloud mode,
 * will register the request with CloudEventBroker and the JSON response when it arrives.
 */
public class HttpJsonRequest implements Runnable, CloudEventBrokerListener {
    private static final Logger logger = Logger.getLogger(HttpJsonRequest.class.getName());
    private static final String TAG = "HttpJsonRequest";

    public URI url;
    public String ipaddr;
    public String method = "GET";
    public String commandId;
    public Map<String, String> headers = new HashMap<String, String>();

    public int readTimeout = 30000;
    public int connectTimeout = 15000;

    CloudEventBroker cloudEventBroker;
    public CloudConnection cloudConnection;

    public AsyncResult<JSONObject> listener;

    CountDownLatch responseReady = new CountDownLatch(1);

    public JSONObject requestBody;

    private boolean attemptedTokenRefresh = false;

    @Override
    public void run() {
        String result = null;
        try {
            logger.finer("Executing JSON request for " + url + " commandId " + commandId);
            if (requestBody != null) {
                logger.finest("Request body: " + requestBody.toString(2));
            }

            //Create a connection
            CloseableHttpClient httpClient = HttpClientBuilder.createHttpClient(url.getHost(), ipaddr);

            HttpRequestBaseHC4 request = null;
            if (method.equals("POST")) {
                request = new HttpPostHC4(url.toString());
            } else if (method.equals("GET")) {
                request = new HttpGetHC4(url.toString());
            }

            // Configure timeouts
            RequestConfig requestConfig = RequestConfig.custom().setConnectionRequestTimeout(connectTimeout).setSocketTimeout(readTimeout).build();
            request.setConfig(requestConfig);

            request.addHeader("Content-Type", "application/json; charset=UTF-8");

            if (cloudConnection != null) {
                request.addHeader("Authorization", cloudConnection.getAccessToken());
            }

            // Set any custom headers
            for (String key : headers.keySet()) {
                request.addHeader(key, headers.get(key));
            }

            // Set the request body
            if (requestBody != null) {
                byte[] bodyBytes = requestBody.toString().getBytes("UTF-8");
                HttpPostHC4 postRequest = (HttpPostHC4)request;
                postRequest.setEntity(new StringEntityHC4(requestBody.toString()));
            }

            if (cloudEventBroker != null) {
                // The actual response will arrive through MQTT .. add the listener
                cloudEventBroker.addListener(this);
            }

            // Connect to our url, get the response
            CloseableHttpResponse response = httpClient.execute(request);

            if (cloudEventBroker == null) {
                // Not using MQTT for this request, so we will have the response here
                String json = EntityUtilsHC4.toString(response.getEntity(), "UTF-8");
                processResponse(json);
            } else {
                // Check for an error sending the request
                if (response.getStatusLine().getStatusCode() != 200) {
                    // 401 can mean our OAuth2 access token has expired. Attempt to refresh it.
                    if (response.getStatusLine().getStatusCode() == 401 && !attemptedTokenRefresh) {
                        attemptedTokenRefresh = true;
                        if (cloudConnection.refreshToken()) {
                            // Retry
                            run();
                            return;
                        }
                    }

                    String responseBody = EntityUtilsHC4.toString(response.getEntity(), "UTF-8");
                    logger.finest(responseBody);
                    listener.onError(new Exception("HTTP response " + response.getStatusLine().toString()));
                    return;
                }

                // Block here for the response
                boolean success = responseReady.await(readTimeout, TimeUnit.MILLISECONDS);
                if (!success) {
                    listener.onError(new TimeoutException());
                }
            }
        } catch (IOException | JSONException e) {
            listener.onError(e);
        } catch (Exception e) {
            listener.onError(e);
        }
    }

    private void processResponse(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            logger.finest("Processing response: " + jsonObject.toString(2));
            listener.onResult(jsonObject);
            return;
        } catch (JSONException e) {
            // Ok that didn't work, let's try converting it to a JSONArray
        }

        // If root waitFobject is an array .. wrap it an an object just so we can return it
        // as a JSONObject.
        try {
            JSONArray jsonArray = new JSONArray(json);
            JSONObject root = new JSONObject();
            root.put("array", jsonArray);
            listener.onResult(root);
        } catch (JSONException e) {
            listener.onError(e);
        }
    }

    /**
     * The cloud event broker will use this to determine which listener to deliver response to.
     * @return
     */
    @Override
    public String getCommandId() {
        return commandId;
    }

    /**
     * The cloud event broker will deliver an MQTT response to this listener via this method.
     * @param json
     */
    @Override
    public void deliverJSONResponse(String json) {
        processResponse(json);
        responseReady.countDown();
    }
}
