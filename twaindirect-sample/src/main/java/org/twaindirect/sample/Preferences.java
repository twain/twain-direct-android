package org.twaindirect.sample;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Simple accessor for the app's shared preferences.
 */

public class Preferences {
    public static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences("org.twaindirect.sample", Context.MODE_PRIVATE);
    }
}
