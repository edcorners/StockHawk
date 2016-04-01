package com.sam_chordas.android.stockhawk.service;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.google.android.gms.gcm.TaskParams;

/**
 * Created by sam_chordas on 10/1/15.
 */
public class StockIntentService extends IntentService {

    public static final String TAG_KEY = "tag";
    public static final String ADD_REQUEST = "add";
    public static final String INIT_REQUEST = "init";
    public static final String PERIODIC_REQUEST = "periodic";
    public static final String SYMBOL_KEY = "symbol";

    public StockIntentService(){
        super(StockIntentService.class.getName());
    }

    public StockIntentService(String name) {
        super(name);
    }

    @Override protected void onHandleIntent(Intent intent) {
        Log.d(StockIntentService.class.getSimpleName(), "Stock Intent Service");
        StockTaskService stockTaskService = new StockTaskService(this);
        Bundle args = new Bundle();
        if (intent.getStringExtra(TAG_KEY).equals(ADD_REQUEST)){
            args.putString(SYMBOL_KEY, intent.getStringExtra(SYMBOL_KEY));
        }
        // We can call OnRunTask from the intent service to force it to run immediately instead of
        // scheduling a task.
        int result = stockTaskService.onRunTask(new TaskParams(intent.getStringExtra(TAG_KEY), args));

    }
}
