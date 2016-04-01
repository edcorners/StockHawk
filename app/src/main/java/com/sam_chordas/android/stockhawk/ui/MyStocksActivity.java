package com.sam_chordas.android.stockhawk.ui;

import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.ActionBar;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.afollestad.materialdialogs.MaterialDialog;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.rest.QuoteCursorAdapter;
import com.sam_chordas.android.stockhawk.rest.RecyclerViewItemClickListener;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.sam_chordas.android.stockhawk.service.StockIntentService;
import com.sam_chordas.android.stockhawk.service.StockTaskService;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.Task;
import com.melnykov.fab.FloatingActionButton;
import com.sam_chordas.android.stockhawk.touch_helper.SimpleItemTouchHelperCallback;

import butterknife.Bind;
import butterknife.ButterKnife;

public class MyStocksActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor>, SharedPreferences.OnSharedPreferenceChangeListener{
    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private String LOG_TAG = MyStocksActivity.class.getSimpleName();
    public static final long PERIODIC_TASK_FREQ_IN_SECS = 1600L;
    public static final long PERIODIC_TASK_FLEX_IN_SECS = 10L;
    private static final int CURSOR_LOADER_ID = 0;

    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence mTitle;
    private Intent mServiceIntent;
    private ItemTouchHelper mItemTouchHelper;
    private QuoteCursorAdapter mCursorAdapter;
    private Context mContext;
    private Cursor mCursor;
    boolean isConnected;

    @Bind(R.id.recycler_view)
    RecyclerView mRecyclerView;
    @Bind(R.id.fab)
    FloatingActionButton mFloatingActionButton;
    @Bind(R.id.recycler_view_container)
    LinearLayout mLinearLayout;
    @Bind(R.id.swipe_refresh_layout)
    SwipeRefreshLayout mSwipeRefreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_stocks);
        ButterKnife.bind(this);
        mContext = this;

        getStockData(savedInstanceState, false);

        initRecyclerView();

        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                getStockData(null, true);
                mSwipeRefreshLayout.setRefreshing(false);
            }
        });

        initAddStockButton();
        initItemTouchHelper();
        createPeriodicTask();

        /*Stetho.initialize(
                Stetho.newInitializerBuilder(this)
                        .enableDumpapp(Stetho.defaultDumperPluginsProvider(this))
                        .enableWebKitInspector(Stetho.defaultInspectorModulesProvider(this))
                        .build());
        OkHttpClient client = new OkHttpClient();
        client.networkInterceptors().add(new StethoInterceptor());*/

    }

    private void initItemTouchHelper() {
        ItemTouchHelper.Callback callback = new SimpleItemTouchHelperCallback(mCursorAdapter);
        mItemTouchHelper = new ItemTouchHelper(callback);
        mItemTouchHelper.attachToRecyclerView(mRecyclerView);

        mTitle = getTitle();
    }

    private void createPeriodicTask() {
        if (isConnected){
            // create a periodic task to pull stocks once every hour after the app has been opened. This
            // is so Widget data stays up to date.
            PeriodicTask periodicTask = new PeriodicTask.Builder()
                    .setService(StockTaskService.class)
                    .setPeriod(PERIODIC_TASK_FREQ_IN_SECS)
                    .setFlex(PERIODIC_TASK_FLEX_IN_SECS)
                    .setTag(StockIntentService.PERIODIC_REQUEST)
                    .setRequiredNetwork(Task.NETWORK_STATE_CONNECTED)
                    .setRequiresCharging(false)
                    .build();
            // Schedule task with tag "periodic." This ensure that only the stocks present in the DB
            // are updated.
            GcmNetworkManager.getInstance(this).schedule(periodicTask);
            // Add a shared pref indicating task has been scheduled
            Utils.setScheduledTaskStatus(mContext, true);
        }
    }

    private void initAddStockButton() {
        //mFloatingActionButton.attachToRecyclerView(mRecyclerView);
        mFloatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isConnected) {
                    new MaterialDialog.Builder(mContext).title(R.string.symbol_search)
                            .content(R.string.content_test)
                            .inputType(InputType.TYPE_CLASS_TEXT)
                            .input(R.string.input_hint, R.string.input_prefill, new MaterialDialog.InputCallback() {
                                @Override
                                public void onInput(MaterialDialog dialog, CharSequence input) {
                                    // On FAB click, receive user input. Make sure the stock doesn't already exist
                                    // in the DB and proceed accordingly
                                    Cursor c = getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                                            new String[]{QuoteColumns.SYMBOL}, QuoteColumns.SYMBOL + "= ?",
                                            new String[]{input.toString().toUpperCase()}, null);
                                    if (c.getCount() != 0) {
                                        Toast toast =
                                                Toast.makeText(MyStocksActivity.this, getString(R.string.stock_already_saved),
                                                        Toast.LENGTH_LONG);
                                        toast.setGravity(Gravity.CENTER, Gravity.CENTER, 0);
                                        toast.show();
                                        return;
                                    } else {
                                        // Add the stock to DB
                                        mServiceIntent.putExtra(StockIntentService.TAG_KEY, StockIntentService.ADD_REQUEST);
                                        mServiceIntent.putExtra(StockIntentService.SYMBOL_KEY, input.toString().toUpperCase());
                                        startService(mServiceIntent);
                                    }
                                }
                            })
                            .show();
                }
            }
        });
    }

    private void getStockData(Bundle savedInstanceState, boolean forceUpdate) {
        ConnectivityManager cm =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
        // The intent service is for executing immediate pulls from the Yahoo API
        // GCMTaskService can only schedule tasks, they cannot execute immediately
        mServiceIntent = new Intent(this, StockIntentService.class);

        if (savedInstanceState == null){
            //Run only if periodic task is unscheduled or forced
            if (isConnected && (!Utils.getScheduledTaskStatus(mContext) || forceUpdate)){
                mServiceIntent.putExtra(StockIntentService.TAG_KEY, StockIntentService.INIT_REQUEST);
                // Run the initialize task service so that some stocks appear upon an empty database
                startService(mServiceIntent);
            }
        }
    }

    @NonNull
    private void initRecyclerView() {
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        getLoaderManager().initLoader(CURSOR_LOADER_ID, null, this);

        mCursorAdapter = new QuoteCursorAdapter(this, null);
        mRecyclerView.addOnItemTouchListener(new RecyclerViewItemClickListener(this,
                new RecyclerViewItemClickListener.OnItemClickListener() {
                    @Override
                    public void onItemClick(View v, int position) {
                        Cursor cursor = mCursorAdapter.getCursor();
                        cursor.moveToPosition(position);
                        int symbolIndex = cursor.getColumnIndex(QuoteColumns.SYMBOL);
                        String symbol = cursor.getString(symbolIndex);
                        Intent graphIntent = new Intent(getApplicationContext(), StockGraphActivity.class);
                        Log.v(LOG_TAG, symbol);
                        graphIntent.setData(QuoteProvider.Quotes.withSymbol(symbol));
                        startActivity(graphIntent);
                    }
                }));
        mRecyclerView.setAdapter(mCursorAdapter);
    }


    @Override
    public void onResume() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.registerOnSharedPreferenceChangeListener(this);
        super.onResume();
        getLoaderManager().restartLoader(CURSOR_LOADER_ID, null, this);
    }

    @Override
    public void onPause() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.my_stocks, menu);
        restoreActionBar();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        if (id == R.id.action_change_units){
            // this is for changing stock changes from percent value to dollar value
            Utils.showPercent = !Utils.showPercent;
            this.getContentResolver().notifyChange(QuoteProvider.Quotes.CONTENT_URI, null);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args){
        // This narrows the return to only the stocks that are most current.
        return new CursorLoader(this, QuoteProvider.Quotes.CONTENT_URI,
                new String[]{ QuoteColumns._ID, QuoteColumns.SYMBOL, QuoteColumns.BIDPRICE,
                        QuoteColumns.PERCENT_CHANGE, QuoteColumns.CHANGE, QuoteColumns.ISUP},
                QuoteColumns.ISCURRENT + " = ?",
                new String[]{"1"},
                null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data){
        mCursorAdapter.swapCursor(data);
        mCursor = data;

        if(data.getCount() > 0 && !isConnected){
            Utils.setDataStatus(mContext,StockTaskService.DATA_STATUS_OUTDATED);
            updateEmptyView();
        }else if(!isConnected){
            Utils.setDataStatus(mContext,StockTaskService.DATA_STATUS_NO_CONNECTION);
            updateEmptyView();
        }

    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader){
        mCursorAdapter.swapCursor(null);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.v(LOG_TAG, "onSharedPreferenceChanged()");
        if (key.equals(getString(R.string.pref_data_status_key))) {
            updateEmptyView();
        }
    }

    /*
    Updates the empty list view with contextually relevant information that the user can
    use to determine why they aren't seeing data.
    */
    private void updateEmptyView() {
        int status = Utils.getDataStatus(this);
        LayoutInflater inflater = LayoutInflater.from(mContext);
        TextView statusTextView = (TextView)inflater.inflate(R.layout.data_status_text_view, null);
        mLinearLayout = (LinearLayout) findViewById(R.id.recycler_view_container);
        if(mLinearLayout.getChildCount() > 1)
            mLinearLayout.removeViewAt(0);
        switch (status) {
            case StockTaskService.DATA_STATUS_NO_CONNECTION:
                statusTextView.setText(R.string.empty_data_list_no_network);
                mLinearLayout.addView(statusTextView, 0);
                break;
            case StockTaskService.DATA_STATUS_OUTDATED:
                statusTextView.setText(R.string.outdated_data_list);
                mLinearLayout.addView(statusTextView, 0);
                break;
            case StockTaskService.DATA_STATUS_SERVER_DOWN:
                statusTextView.setText(R.string.empty_data_list_server_down);
                mLinearLayout.addView(statusTextView, 0);
                break;
            case StockTaskService.DATA_STATUS_CONNECTION_RESTORED:
                onConnectionRestored();
                break;
            case StockTaskService.DATA_STATUS_NOT_FOUND:
                Toast.makeText(mContext, getString(R.string.stock_symbol_not_found), Toast.LENGTH_SHORT).show();
                Utils.setDataStatus(mContext, StockTaskService.DATA_STATUS_OK);
                break;
        }
    }

    private void onConnectionRestored() {
        getStockData(null, true);
        initAddStockButton();
        initItemTouchHelper();
    }

}
