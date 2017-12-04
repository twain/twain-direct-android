package org.twaindirect.session;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Helper methods.
 */

public class StreamUtils {
    // Read a string from an inputStream and return a JSONObject
    public static JSONObject inputStreamToJSONObject(InputStream inputStream) throws IOException, JSONException {
        JSONObject jsonObject = new JSONObject(inputStreamToString(inputStream));
        return jsonObject;
    }

    // Read a string from an InputStream
    public static String inputStreamToString(InputStream inputStream) throws IOException {
        InputStreamReader streamReader = new InputStreamReader(inputStream);
        BufferedReader reader = new BufferedReader(streamReader);
        StringBuilder stringBuilder = new StringBuilder();
        //Check if the line we are reading is not null
        String inputLine;
        while((inputLine = reader.readLine()) != null){
            stringBuilder.append(inputLine);
        }
        reader.close();
        streamReader.close();
        return stringBuilder.toString();
    }


}
