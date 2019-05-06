package org.twaindirect.cloud;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGetHC4;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtilsHC4;
import org.twaindirect.session.AsyncResult;
import org.twaindirect.session.HttpClientBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Download an image block from the cloud endpoint.
 * This is a simple HTTP request that returns base64
 * encoded image data.
 */
public class CloudBlockRequest implements Runnable {
    private static final Logger logger = Logger.getLogger(CloudBlockRequest.class.getName());

    public CloudBlockRequest(CloudConnection cloudConnection) {
        this.cloudConnection = cloudConnection;
    }

    // The block's cloud URL
    public URI url;

    // Headers
    public Map<String, String> headers = new HashMap<String, String>();

    // The downloaded image is delivered to listener
    public AsyncResult<InputStream> listener;

    // Read timeout in milliseconds
    public int readTimeout = 30000;

    // Connect timeout in milliseconds
    public int connectTimeout = 20000;

    // We use this to get the access token and refresh it if required
    private final CloudConnection cloudConnection;

    // Have we already attempted to refresh an expired access token?
    private boolean attemptedTokenRefresh = false;

    @Override
    public void run() {
        String result = null;
        try {
            logger.info("Requesting image block from " + url.toString());

            //Create a connection
            CloseableHttpClient httpClient = HttpClientBuilder.createHttpClient(url.getHost(), null);
            HttpGetHC4 request = new HttpGetHC4(url.toString());

            RequestConfig requestConfig = RequestConfig.custom().setConnectionRequestTimeout(connectTimeout).setSocketTimeout(readTimeout).build();
            request.setConfig(requestConfig);

            request.addHeader("Content-Type", "application/json; charset=UTF-8");
            request.addHeader("Authorization", cloudConnection.getAccessToken());

            // Set any custom headers
            for (String key : headers.keySet()) {
                request.addHeader(key, headers.get(key));
            }

            // Send the request, pass on the response
            CloseableHttpResponse response = httpClient.execute(request);

            // 401 can mean our OAuth2 access token has expired. Attempt to refresh it.
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

            listener.onResult(response.getEntity().getContent());
        } catch (IOException e) {
            listener.onError(e);
        }
    }
}
