package org.twaindirect.cloud;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGetHC4;
import org.apache.http.client.methods.HttpRequestBaseHC4;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtilsHC4;
import org.json.JSONArray;
import org.json.JSONObject;
import org.twaindirect.session.AsyncResult;
import org.twaindirect.session.HttpClientBuilder;
import org.twaindirect.session.HttpJsonRequest;
import org.twaindirect.session.URIUtils;

import java.io.IOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Manage a connection to a TWAIN Cloud service.
 * This includes the REST API and the MQTT events listener.
 */
public class CloudConnection {
    private static final Logger logger = Logger.getLogger(CloudConnection.class.getName());

    // URL for the REST API
    private URI apiUrl;

    // OAuth2 Access Token
    private String accessToken;

    // OAuth2 Refresh Token
    private String refreshToken;

    // Are we currently in the middle of a token refresh?
    // If so, when another request fails, hold on to it until
    // the refresh completes and use the new token.
    private boolean refreshingToken = false;

    private ExecutorService executor = Executors.newFixedThreadPool(1);

    // Interface used to listen for token refreshes so the updated
    // tokens can be saved for the next session.
    public interface TokenRefreshListener {
        void onAccessTokenRefreshed(CloudConnection cloudConnection);
    }

    public TokenRefreshListener tokenRefreshListener;

    public CloudConnection(URI apiUrl, String accessToken, String refreshToken) {
        this.apiUrl = apiUrl;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    /**
     * Request a list of scanners from the service.
     */
    public void getScannerList(final AsyncResult<List<CloudScannerInfo>> response) {
        getEventBrokerInfo(new AsyncResult<CloudEventBrokerInfo>() {
            @Override
            public void onResult(final CloudEventBrokerInfo eventBrokerInfo) {
                getScannerListJSON(new AsyncResult<JSONObject>() {
                    @Override
                    public void onResult(JSONObject result) {
                        ArrayList<CloudScannerInfo> cloudScanners = new ArrayList<>();
                        if (!result.has("array")) {
                            // Didn't get any scanners in the response
                            response.onResult(cloudScanners);
                            return;
                        }

                        // Turn the JSON array into an array of CloudScannerInfo to return
                        JSONArray scanners = result.getJSONArray("array");
                        for (int idx=0; idx<scanners.length(); idx++) {
                            JSONObject scannerCloudInfo = scanners.getJSONObject(idx);

                            CloudScannerInfo csi = new CloudScannerInfo(apiUrl, eventBrokerInfo, scannerCloudInfo);
                            cloudScanners.add(csi);
                        }

                        response.onResult(cloudScanners);
                    }

                    @Override
                    public void onError(Exception e) {
                        response.onError(e);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                response.onError(e);
            }
        });
    }

    /**
     * Fetch the scanner list JSON
     */
    private void getScannerListJSON(final AsyncResult<JSONObject> response) {
        // First request the user endpoint, so we know the MQTT response topic to subscribe to
        HttpJsonRequest request = new HttpJsonRequest();
        request.url = URIUtils.appendPathToURI(apiUrl, "/scanners");
        request.method = "GET";
        request.cloudConnection = this;

        request.listener = new AsyncResult<JSONObject>() {
            @Override
            public void onResult(JSONObject result) {
                response.onResult(result);
            }

            @Override
            public void onError(Exception e) {
                response.onError(e);
            }
        };

        executor.submit(request);
    }

    /**
     * Fetch the /user endpoint and extract the EventBroker info
     */
    public void getEventBrokerInfo(final AsyncResult<CloudEventBrokerInfo> response) {
        // First request the user endpoint, so we know the MQTT response topic to subscribe to
        HttpJsonRequest request = new HttpJsonRequest();
        request.url = URIUtils.appendPathToURI(apiUrl, "/user");
        request.method = "GET";
        request.cloudConnection = this;

        request.listener = new AsyncResult<JSONObject>() {
            @Override
            public void onResult(JSONObject result) {
                if (!result.has("eventBroker")) {
                    if (result.has("message")) {
                        String message = result.getString("message");
                        response.onError(new Exception(message));
                        return;
                    }

                    response.onError(new Exception("eventBroker key missing from user JSON response"));
                    return;
                }

                CloudEventBrokerInfo eventBrokerInfo = new CloudEventBrokerInfo();

                JSONObject eventBroker = result.getJSONObject("eventBroker");
                eventBrokerInfo.topic = eventBroker.getString("topic");
                eventBrokerInfo.type = eventBroker.getString("type");
                eventBrokerInfo.url = eventBroker.getString("url");

                response.onResult(eventBrokerInfo);
            }

            @Override
            public void onError(Exception e) {
                response.onError(e);
            }
        };

        executor.submit(request);
    }

    /**
     * Fetch info for the specified scanner.
     * Returns the JSON we received from the cloud service.
     */
    public void getScannerInfoJSON(String scannerId, final AsyncResult<JSONObject> response) {
        HttpJsonRequest request = new HttpJsonRequest();
        request.url = URIUtils.appendPathToURI(apiUrl, "/scanners/" + scannerId);
        request.method = "GET";
        request.cloudConnection = this;

        request.listener = new AsyncResult<JSONObject>() {
            @Override
            public void onResult(JSONObject result) {
                if (!result.has("eventBroker")) {
                    if (result.has("message")) {
                        String message = result.getString("message");
                        response.onError(new Exception(message));
                        return;
                    }

                    response.onError(new Exception("eventBroker key missing from user JSON response"));
                    return;
                }

                response.onResult(result);
            }

            @Override
            public void onError(Exception e) {
                response.onError(e);
            }
        };

        executor.submit(request);
    }

    /**
     * Synchronously refresh the access token.
     * @return
     */
    public boolean refreshToken() {
        // If more than one request calls refreshToken, block the other requests until the
        // first one finishes.
        synchronized(this) {
            if (refreshingToken) {
                return true;
            }

            refreshingToken = true;

            logger.fine("Refreshing OAuth2 access token");

            URI uri = URIUtils.appendPathToURI(apiUrl, "/authentication/refresh/" + refreshToken);

            CloseableHttpClient httpClient = null;
            try {
                httpClient = HttpClientBuilder.createHttpClient(uri.getHost(), null);
                HttpRequestBaseHC4 request = new HttpGetHC4(uri.toString());
                request.addHeader("Authorization", accessToken);
                CloseableHttpResponse response = httpClient.execute(request);
                if (response.getStatusLine().getStatusCode() != 200) {
                    logger.warning("Token refresh returned " + response.getStatusLine().toString());
                    return false;
                }
                String json = EntityUtilsHC4.toString(response.getEntity(), "UTF-8");
                JSONObject jsonObject = new JSONObject(json);
                accessToken = jsonObject.getString("authorizationToken");
                refreshToken = jsonObject.getString("refreshToken");
                if (tokenRefreshListener != null) {
                    tokenRefreshListener.onAccessTokenRefreshed(this);
                }
                logger.fine("Token refresh successful");
                return true;
            } catch (UnknownHostException e) {
                logger.warning(e.getMessage());
                return false;
            } catch (IOException e) {
                logger.warning(e.getMessage());
                return false;
            }
        }
    }

    public URI getApiUrl() {
        return apiUrl;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }
}
