package com.meancoder.livetextfinder;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferencesManager {

    SharedPreferences pref;
    SharedPreferences.Editor editor;
    Context _context;

    // shared pref mode
    int PRIVATE_MODE = 0;

    // Shared preferences file name
    private static final String PREF_NAME = "meancoder_livetextfinder_prefs";

    // Shared preference variable name
    private static final String IS_FIRST_TIME_LAUNCH = "IsFirstTimeLaunch";

    // Constructor
    public PreferencesManager(Context context) {
        this._context = context;
        pref = _context.getSharedPreferences(PREF_NAME, PRIVATE_MODE);
        editor = pref.edit();
    }

    // This method to be used as soon as the fist time launch is completed to update the
// shared preference
    public void setFirstTimeLaunch(boolean isFirstTime) {
        editor.putBoolean(IS_FIRST_TIME_LAUNCH, isFirstTime);
        editor.commit();
    }

    // This method will return true of the app is launched for the first time. false if
// launched already
    public boolean isFirstTimeLaunch() {
        return pref.getBoolean(IS_FIRST_TIME_LAUNCH, true);
    }
}