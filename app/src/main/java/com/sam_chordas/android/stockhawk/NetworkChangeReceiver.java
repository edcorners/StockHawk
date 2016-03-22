package com.sam_chordas.android.stockhawk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.util.Log;

import com.sam_chordas.android.stockhawk.rest.Utils;
import com.sam_chordas.android.stockhawk.service.StockTaskService;
import com.sam_chordas.android.stockhawk.ui.MyStocksActivity;

/**
 * Created by Edison on 3/17/2016.
 */
public class NetworkChangeReceiver extends BroadcastReceiver {

    private String LOG_TAG = NetworkChangeReceiver.class.getSimpleName();

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final ConnectivityManager connMgr = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connMgr.getActiveNetworkInfo() != null) {
            Log.d(LOG_TAG, "Network ON");
            Utils.setDataStatus(context, StockTaskService.DATA_STATUS_CONNECTION_RESTORED);
        }else{
            Log.d(LOG_TAG, "Network OFF" );
            Utils.setDataStatus(context, StockTaskService.DATA_STATUS_OUTDATED);
        }
    }
}

