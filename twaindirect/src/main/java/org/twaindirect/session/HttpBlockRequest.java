package org.twaindirect.session;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPostHC4;
import org.apache.http.entity.StringEntityHC4;
import org.apache.http.impl.client.CloseableHttpClient;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;


/**
 * Download an image block.  Image blocks are delivered as a MIME body with the
 * metadata as an application/json part, and the image as an application/pdf part.
 */
class HttpBlockRequest implements Runnable {
    private static final String TAG = "HttpBlockRequest";

    public URL url;
    public String ipaddr;
    public Map<String, String> headers = new HashMap<String, String>();

    AsyncResult<InputStream> listener;
    public JSONObject requestBody;

    public int readTimeout = 30000;
    public int connectTimeout = 20000;

    @Override
    public void run() {
        String result = null;
        try {
            //Create a connection
            CloseableHttpClient httpClient =  HttpClientBuilder.createHttpClient(url.getHost(), ipaddr);
            HttpPostHC4 request = new HttpPostHC4(url.toString());

            RequestConfig requestConfig = RequestConfig.custom().setConnectionRequestTimeout(connectTimeout).setSocketTimeout(readTimeout).build();
            request.setConfig(requestConfig);

            request.addHeader("Content-Type", "application/json; charset=UTF-8");

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

            // Connect to our url, get the response
            CloseableHttpResponse response = httpClient.execute(request);
            listener.onResult(response.getEntity().getContent());
        } catch (IOException e) {
            listener.onError(e);
        }
    }
}
