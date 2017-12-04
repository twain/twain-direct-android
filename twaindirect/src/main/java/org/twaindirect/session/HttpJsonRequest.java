package org.twaindirect.session;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGetHC4;
import org.apache.http.client.methods.HttpPostHC4;
import org.apache.http.client.methods.HttpRequestBaseHC4;
import org.apache.http.entity.StringEntityHC4;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtilsHC4;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

class HttpJsonRequest implements Runnable {
    private static final String TAG = "HttpJsonRequest";

    public URL url;
    public String ipaddr;
    public String method = "GET";
    public Map<String, String> headers = new HashMap<String, String>();

    public int readTimeout = 30000;
    public int connectTimeout = 15000;

    AsyncResult<JSONObject> listener;

    public JSONObject requestBody;

    @Override
    public void run() {
        String result = null;
        try {
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

            // Turn into a JSONObject and return it
            String json = EntityUtilsHC4.toString(response.getEntity(), "UTF-8");
            JSONObject jsonObject = new JSONObject(json);
            listener.onResult(jsonObject);
        } catch (IOException | JSONException e) {
            listener.onError(e);
        }
    }
}
