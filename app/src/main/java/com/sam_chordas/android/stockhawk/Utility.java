package com.sam_chordas.android.stockhawk;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.sam_chordas.android.stockhawk.service.StockTaskService;

/**
 * Created by Edison on 3/17/2016.
 */
public class Utility {
    public static int getDataStatus(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getInt(context.getString(R.string.pref_data_status_key), StockTaskService.DATA_STATUS_OK);
    }

    public static void setDataStatus(Context context, @StockTaskService.DataStatus int status) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor spe = sp.edit();
        spe.putInt(context.getString(R.string.pref_data_status_key), status);
        spe.apply();
    }
}
