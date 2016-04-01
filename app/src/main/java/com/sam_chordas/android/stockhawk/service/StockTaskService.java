package com.sam_chordas.android.stockhawk.service;

import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.annotation.IntDef;
import android.util.Log;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.TaskParams;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URLEncoder;
import java.util.ArrayList;

/**
 * Created by sam_chordas on 9/30/15.
 * The GCMTask service is primarily for periodic tasks. However, OnRunTask can be called directly
 * and is used for the initialization and adding task as well.
 */
public class StockTaskService extends GcmTaskService{
    private String LOG_TAG = StockTaskService.class.getSimpleName();

    private OkHttpClient client = new OkHttpClient();
    private Context mContext;
    private StringBuilder mStoredSymbols = new StringBuilder();
    private boolean isUpdate;

    public StockTaskService(){}

    public StockTaskService(Context context){
        mContext = context;
    }

    @Override
    public int onRunTask(TaskParams params){
        Log.v(LOG_TAG, "Starting stock task service");
        Cursor initQueryCursor;
        if (mContext == null){
            mContext = this;
        }
        StringBuilder urlStringBuilder = prepareURL(params);
        int result = updateDatabase(urlStringBuilder);

        return result;
    }

    private int updateDatabase(StringBuilder urlStringBuilder) {
        String urlString;
        String getResponse;
        int result = GcmNetworkManager.RESULT_FAILURE;

        if (urlStringBuilder != null){
            urlString = urlStringBuilder.toString();
            try{
                getResponse = fetchData(urlString);
                result = GcmNetworkManager.RESULT_SUCCESS;

                ArrayList quoteContentValues = Utils.quoteJsonToContentVals(getResponse);
                if(!quoteContentValues.isEmpty()) {
                    ContentValues contentValues = new ContentValues();
                    // update ISCURRENT to 0 (false) so new data is current
                    if (isUpdate) {
                        contentValues.put(QuoteColumns.ISCURRENT, 0);
                        mContext.getContentResolver().update(QuoteProvider.Quotes.CONTENT_URI, contentValues,
                                null, null);
                    }
                    mContext.getContentResolver().applyBatch(QuoteProvider.AUTHORITY, quoteContentValues);
                    setDataStatus(DATA_STATUS_OK);
                }else{
                    setDataStatus(DATA_STATUS_NOT_FOUND);
                }
            } catch (IOException e){
                e.printStackTrace();
                setDataStatus(DATA_STATUS_SERVER_DOWN);
            } catch (RemoteException | OperationApplicationException e){
                Log.e(LOG_TAG, "Error applying batch insert", e);
                setDataStatus(DATA_STATUS_OUTDATED);
            }
        }
        return result;
    }

    private String fetchData(String url) throws IOException{
        Request request = new Request.Builder()
                .url(url)
                .build();

        Log.v(LOG_TAG, "Call URI: " + request.toString());
        Response response = client.newCall(request).execute();
        return response.body().string();
    }

    private StringBuilder prepareURL(TaskParams params) {
        StringBuilder urlStringBuilder = new StringBuilder();
        try{
            // Base URL for the Yahoo query
            urlStringBuilder.append("https://query.yahooapis.com/v1/public/yql?q=");
            urlStringBuilder.append(URLEncoder.encode("select * from yahoo.finance.quotes where symbol "
                    + "in (", "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        if (params.getTag().equals(StockIntentService.INIT_REQUEST) || params.getTag().equals(StockIntentService.PERIODIC_REQUEST)){
            prepareUpdateURL(urlStringBuilder);
        } else if (params.getTag().equals(StockIntentService.ADD_REQUEST)){
            prepareAddURL(params, urlStringBuilder);
        }
        // finalize the URL for the API query.
        urlStringBuilder.append("&format=json&diagnostics=true&env=store%3A%2F%2Fdatatables."
                + "org%2Falltableswithkeys&callback=");
        return urlStringBuilder;
    }

    private void prepareAddURL(TaskParams params, StringBuilder urlStringBuilder) {
        isUpdate = false;
        // get symbol from params.getExtra and build query
        String stockInput = params.getExtras().getString(StockIntentService.SYMBOL_KEY);
        try {
            urlStringBuilder.append(URLEncoder.encode("\"" + stockInput + "\")", "UTF-8"));
        } catch (UnsupportedEncodingException e){
            e.printStackTrace();
        }
    }

    private void prepareUpdateURL(StringBuilder urlStringBuilder) {
        Cursor initQueryCursor;
        isUpdate = true;
        initQueryCursor = mContext.getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                new String[] { "Distinct " + QuoteColumns.SYMBOL }, null,
                null, null);
        if (initQueryCursor.getCount() == 0 || initQueryCursor == null){
            // Init task. Populates DB with quotes for the symbols seen below
            try {
                urlStringBuilder.append(
                        URLEncoder.encode("\"YHOO\",\"AAPL\",\"GOOG\",\"MSFT\")", "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        } else if (initQueryCursor != null){
            DatabaseUtils.dumpCursor(initQueryCursor);
            initQueryCursor.moveToFirst();
            for (int i = 0; i < initQueryCursor.getCount(); i++){
                mStoredSymbols.append("\""+
                        initQueryCursor.getString(initQueryCursor.getColumnIndex(QuoteColumns.SYMBOL))+"\",");
                initQueryCursor.moveToNext();
            }
            mStoredSymbols.replace(mStoredSymbols.length() - 1, mStoredSymbols.length(), ")");
            try {
                urlStringBuilder.append(URLEncoder.encode(mStoredSymbols.toString(), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({DATA_STATUS_OK, DATA_STATUS_SERVER_DOWN, DATA_STATUS_OUTDATED, DATA_STATUS_NO_CONNECTION,DATA_STATUS_CONNECTION_RESTORED,DATA_STATUS_NOT_FOUND})
    public @interface DataStatus {}

    public static final int DATA_STATUS_OK = 0;
    public static final int DATA_STATUS_SERVER_DOWN = 1;
    public static final int DATA_STATUS_OUTDATED = 2;
    public static final int DATA_STATUS_NO_CONNECTION = 3;
    public static final int DATA_STATUS_CONNECTION_RESTORED = 4;
    public static final int DATA_STATUS_NOT_FOUND = 5;


    private void setDataStatus(@DataStatus int status){
        Log.v(LOG_TAG, "Changing pref to:"+ status);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor spe = sp.edit();
        spe.putInt(mContext.getString(R.string.pref_data_status_key), status);
        spe.commit();
    }

}
