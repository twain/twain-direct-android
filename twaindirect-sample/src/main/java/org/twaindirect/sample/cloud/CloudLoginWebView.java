package org.twaindirect.sample.cloud;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.twaindirect.sample.R;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public class CloudLoginWebView extends AppCompatActivity {
    private WebView webView;

    public static final String AUTH_TOKEN_KEY = "authToken";
    public static final String REFRESH_TOKEN_KEY = "refreshToken";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cloud_login_web_view);

        String url = getIntent().getStringExtra("url");
        WebView webView = (WebView)findViewById(R.id.web_view);

        // We have to fudge the User-Agent so Google's OAuth login page will accept us.
        // Google has a native login SDK that would be the correct way to do this.
        webView.getSettings().setUserAgentString("Mozilla/5.0 (Linux; Android 8.0.0; Pixel 2 XL Build/OPD1.170816.004) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.125 Mobile Safari/537.36\n");
        webView.getSettings().setJavaScriptEnabled(true);

        webView.loadUrl(url);
        webView.setWebViewClient(new WebViewClient() {
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Check for a redirect containing the auth and refresh tokens
                try {
                    URI uri = new URI(url);

                    // Looking for a redirect to this specific URL
                    if (uri.getHost().equals("twain.hazybits.com")) {
                        String authToken = null;
                        String refreshToken = null;

                        // Extract the authorization token and refresh token from the redirect URL
                        List<NameValuePair> queryParameters = URLEncodedUtils.parse(uri, "UTF-8");
                        for (NameValuePair param : queryParameters) {
                            if (param.getName().equals("authorization_token")) {
                                authToken = param.getValue();
                            }
                            if (param.getName().equals("refresh_token")) {
                                refreshToken = param.getValue();
                            }
                        }

                        // If we found them, we're done.
                        if (authToken != null && refreshToken != null) {
                            Intent intent = new Intent();
                            intent.putExtra(AUTH_TOKEN_KEY, authToken);
                            intent.putExtra(REFRESH_TOKEN_KEY, refreshToken);
                            setResult(0, intent);
                            finish();
                        }
                    }
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }

                // Continue loading
                return false;
            }
        });

    }
}
