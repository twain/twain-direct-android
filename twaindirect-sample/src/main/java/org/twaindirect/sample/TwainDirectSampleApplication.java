package org.twaindirect.sample;

import android.app.Application;
import android.content.SharedPreferences;

import org.twaindirect.cloud.CloudConnection;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Custom Application instance, so we have a place to store information shared
 * across various parts of the app.
 */
public class TwainDirectSampleApplication extends Application {
    /**
     * The cloud connection is global state, shared between all the cloud client request
     * objects, so that they can all coordinate on refreshing the OAuth2 refresh token
     * when required. Initialize this from saved state at startup.
     */
    public CloudConnection cloudConnection;

    private final static String ACCESS_TOKEN_KEY = "accessToken";
    private final static String REFRESH_TOKEN_KEY = "refreshToken";
    private final static String CLOUD_API_URL_KEY = "apiUrl";

    @Override
    public void onCreate() {
        super.onCreate();

        restoreCloudConnection();
    }

    /**
     * If we have saved TWAIN Cloud access and refresh tokens from a previous
     * session, restore those here.
     */
    private void restoreCloudConnection() {
        SharedPreferences preferences = Preferences.getSharedPreferences(this);
        String accessToken = preferences.getString(ACCESS_TOKEN_KEY, null);
        String refreshToken = preferences.getString(REFRESH_TOKEN_KEY, null);
        String apiUrlString = preferences.getString(CLOUD_API_URL_KEY, null);
        if (accessToken != null && refreshToken != null && apiUrlString != null) {
            // Use the saved CloudConnection
            try {
                URI apiUrl = new URI(apiUrlString);
                cloudConnection = new CloudConnection(apiUrl, accessToken, refreshToken);

                // Listen for token refreshes and save the updated values to Preferences
                cloudConnection.tokenRefreshListener = new CloudConnection.TokenRefreshListener() {
                    @Override
                    public void onAccessTokenRefreshed(CloudConnection cloudConnection) {
                        saveCloudConnectionTokens();
                    }
                };
            } catch (URISyntaxException e) {
                // saved cloudConnection URI was malformed, leave it null
            }
        }
    }

    /**
     * Write the cloudConnection tokens to preferences
     */
    public void saveCloudConnectionTokens() {
        if (cloudConnection != null) {
            SharedPreferences.Editor editor = Preferences.getSharedPreferences(this).edit();
            editor.putString(ACCESS_TOKEN_KEY, cloudConnection.getAccessToken());
            editor.putString(REFRESH_TOKEN_KEY, cloudConnection.getRefreshToken());
            editor.putString(CLOUD_API_URL_KEY, cloudConnection.getApiUrl().toString());
            editor.apply();
        }
    }
}
