package com.sam_chordas.android.stockhawk.rest;

import android.annotation.TargetApi;
import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.TextView;

import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.service.StockTaskService;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by sam_chordas on 10/8/15.
 */
public class Utils {

    private static String LOG_TAG = Utils.class.getSimpleName();
    public static final SimpleDateFormat fullDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    public static final SimpleDateFormat shortDateTimeFormat = new SimpleDateFormat("MM-dd HH:mm");
    public static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    public static boolean showPercent = true;

    public static ArrayList quoteJsonToContentVals(String JSON){
        ArrayList<ContentProviderOperation> batchOperations = new ArrayList<>();
        JSONObject jsonObject = null;
        JSONArray resultsArray = null;
        Log.i(LOG_TAG, "GET FB: " +JSON);
        try{
            jsonObject = new JSONObject(JSON);
            if (jsonObject != null && jsonObject.length() != 0){
                jsonObject = jsonObject.getJSONObject("query");
                int count = Integer.parseInt(jsonObject.getString("count"));
                if (count == 1){
                    jsonObject = jsonObject.getJSONObject("results")
                            .getJSONObject("quote");
                    String bid = jsonObject.getString("Bid");
                    if(bid != null && !bid.equals("null")) {
                        batchOperations.add(buildBatchOperation(jsonObject));
                    }
                } else{
                    resultsArray = jsonObject.getJSONObject("results").getJSONArray("quote");

                    if (resultsArray != null && resultsArray.length() != 0){
                        for (int i = 0; i < resultsArray.length(); i++){
                            jsonObject = resultsArray.getJSONObject(i);
                            String bid = jsonObject.getString("Bid");
                            Log.v(LOG_TAG, "bid:"+bid);
                            if(bid != null && !bid.equals("null")) {
                                batchOperations.add(buildBatchOperation(jsonObject));
                            }
                        }
                    }
                }
            }
        } catch (JSONException e){
            Log.e(LOG_TAG, "String to JSON failed: " + e);
        }
        return batchOperations;
    }

    public static String truncateBidPrice(String bidPrice){
        bidPrice = String.format("%.2f", Float.parseFloat(bidPrice));
        return bidPrice;
    }

    public static String truncateChange(String change, boolean isPercentChange){
        String weight = change.substring(0, 1);
        String ampersand = "";
        if (isPercentChange){
            ampersand = change.substring(change.length() - 1, change.length());
            change = change.substring(0, change.length() - 1);
        }
        change = change.substring(1, change.length());
        double round = (double) Math.round(Double.parseDouble(change) * 100) / 100;
        change = String.format("%.2f", round);
        StringBuffer changeBuffer = new StringBuffer(change);
        changeBuffer.insert(0, weight);
        changeBuffer.append(ampersand);
        change = changeBuffer.toString();
        return change;
    }

    public static ContentProviderOperation buildBatchOperation(JSONObject jsonObject){
        ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(
                QuoteProvider.Quotes.CONTENT_URI);
        try {
            String change = jsonObject.getString("Change");
            builder.withValue(QuoteColumns.SYMBOL, jsonObject.getString("symbol"));
            builder.withValue(QuoteColumns.BIDPRICE, truncateBidPrice(jsonObject.getString("Bid")));
            String changeinPercent = jsonObject.getString("ChangeinPercent");

            if(changeinPercent != null && !changeinPercent.equals("null")) {
                builder.withValue(QuoteColumns.PERCENT_CHANGE, truncateChange(
                        changeinPercent, true));
            }else{
                builder.withValue(QuoteColumns.PERCENT_CHANGE, "0");
            }

            if(change != null && !change.equals("null")) {
                builder.withValue(QuoteColumns.CHANGE, truncateChange(change, false));
            }else{
                builder.withValue(QuoteColumns.CHANGE, "0");
            }

            builder.withValue(QuoteColumns.ISCURRENT, 1);
            if (change.charAt(0) == '-') {
                builder.withValue(QuoteColumns.ISUP, 0);
            } else {
                builder.withValue(QuoteColumns.ISUP, 1);
            }
            builder.withValue(QuoteColumns.CREATED, getCurrentDateTime());
        } catch (JSONException e){
            e.printStackTrace();
        }
        return builder.build();
    }

    private static String getCurrentDateTime() {
        return fullDateTimeFormat.format(Calendar.getInstance().getTime());
    }

    public static String formatForGraph(String date){
        String dateForGraph = date;
        try {
            Date parsedDate = fullDateTimeFormat.parse(date);
            Calendar today = Calendar.getInstance();
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);
            if(DateUtils.isSameDay(today.getTime(), parsedDate)){
                dateForGraph = timeFormat.format(parsedDate);
            }else if(DateUtils.isSameDay(yesterday.getTime(), parsedDate)){
                dateForGraph = shortDateTimeFormat.format(parsedDate);
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return dateForGraph;
    }

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

    public static boolean getScheduledTaskStatus(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getBoolean(context.getString(R.string.pref_scheduled_task_status_key), false);
    }

    public static void setScheduledTaskStatus(Context context, boolean started) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor spe = sp.edit();
        spe.putBoolean(context.getString(R.string.pref_scheduled_task_status_key), started);
        spe.apply();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static void createListItemView(Context context, Cursor data, TextView symbolTextView, TextView bidTextView, TextView changeTextView) {
        int sdk = Build.VERSION.SDK_INT;
        boolean isUp = data.getInt(data.getColumnIndex(QuoteColumns.ISUP)) == 1;
        String bid = data.getString(data.getColumnIndex(QuoteColumns.BIDPRICE));
        if (isUp) {
            if (sdk < Build.VERSION_CODES.JELLY_BEAN) {
                changeTextView.setBackgroundDrawable(
                        context.getResources().getDrawable(R.drawable.percent_change_pill_green));
            } else {
                changeTextView.setBackground(
                        context.getResources().getDrawable(R.drawable.percent_change_pill_green));
            }
        } else {
            if (sdk < Build.VERSION_CODES.JELLY_BEAN) {
                changeTextView.setBackgroundDrawable(
                        context.getResources().getDrawable(R.drawable.percent_change_pill_red));
            } else {
                changeTextView.setBackground(
                        context.getResources().getDrawable(R.drawable.percent_change_pill_red));
            }
        }

        String symbol = data.getString(data.getColumnIndex(QuoteColumns.SYMBOL));
        symbolTextView.setText(symbol);
        symbolTextView.setContentDescription(symbol.replace("",".").substring(1));// Separates letters so it reads em separately
        bidTextView.setText(bid);
        bidTextView.setContentDescription(String.format(context.getString(R.string.cd_stock_value_per_share), bid));
        if (Utils.showPercent) {
            String percent_change = data.getString(data.getColumnIndex(QuoteColumns.PERCENT_CHANGE));
            changeTextView.setText(percent_change);
            double percentDouble = Double.parseDouble(percent_change.trim().replace("%", ""));
            if(isUp){
                changeTextView.setContentDescription(String.format(context.getString(R.string.cd_stock_value_up_percentage), Math.abs(percentDouble)));
            }else{
                changeTextView.setContentDescription(String.format(context.getString(R.string.cd_stock_value_down_percentage), Math.abs(percentDouble)));
            }
        } else {
            String change = data.getString(data.getColumnIndex(QuoteColumns.CHANGE));
            changeTextView.setText(change);
            double changeValue = Double.parseDouble(change.trim());
            if(isUp){
                changeTextView.setContentDescription(String.format(context.getString(R.string.cd_stock_value_up_points), Math.abs(changeValue)));
            }else{
                changeTextView.setContentDescription(String.format(context.getString(R.string.cd_stock_value_down_points), Math.abs(changeValue)));
            }
        }
    }

}
