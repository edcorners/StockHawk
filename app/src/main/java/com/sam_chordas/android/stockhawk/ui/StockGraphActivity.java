package com.sam_chordas.android.stockhawk.ui;

import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.db.chart.model.LineSet;
import com.db.chart.model.Point;
import com.db.chart.view.ChartView;
import com.db.chart.view.LineChartView;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.rest.Utils;

import butterknife.Bind;
import butterknife.ButterKnife;

public class StockGraphActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    private final int LOADER_ID = 0;
    private final String LOG_TAG = StockGraphActivity.class.getSimpleName();


    @Bind(R.id.linechart)
    LineChartView lineChartView;
    @Bind(R.id.graph_linear_layout)
    LinearLayout graphLinearLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_graph);
        ButterKnife.bind(this);
        //getActionBar().setDisplayHomeAsUpEnabled(true);
        getLoaderManager().initLoader(LOADER_ID, null, this);

    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {


        Uri uri = getIntent().getData();
        Log.v(LOG_TAG, uri.toString());

        return new CursorLoader(this, uri,
                new String[]{QuoteColumns.SYMBOL, QuoteColumns.BIDPRICE, QuoteColumns.PERCENT_CHANGE, QuoteColumns.CREATED},
                null,
                null,
                QuoteColumns._ID +" DESC LIMIT 7"); // Limit query to last 15
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (data.getCount() > 0) {
            data.move(data.getCount()+1);

            LineSet dataset = new LineSet();
            double min = Double.MAX_VALUE;
            double max = Double.MIN_VALUE;

            while (data.moveToPrevious()) {

                String bid = data.getString(1).replace(",", ".");
                String created = data.getString(3);
                float bidFloat = Float.parseFloat(bid);
                double bidDouble = Double.parseDouble(bid);
                dataset.addPoint(new Point(Utils.formatForGraph(created), bidFloat));


                min = min > bidDouble ? bidDouble: min;
                max = max < bidDouble ? bidDouble: max;
                if(data.isFirst()){
                    LinearLayout listItemQuoteLinearLayout = (LinearLayout) getLayoutInflater().inflate(R.layout.list_item_quote, null);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.MATCH_PARENT, 4.0f);
                    listItemQuoteLinearLayout.setLayoutParams(params);
                    if(graphLinearLayout.getChildCount() > 1) {
                        graphLinearLayout.removeViewAt(0);
                    }
                    TextView symbolTextView = (TextView) listItemQuoteLinearLayout.findViewById(R.id.stock_symbol);
                    TextView bidTextView = (TextView) listItemQuoteLinearLayout.findViewById(R.id.bid_price);
                    TextView changeTextView = (TextView) listItemQuoteLinearLayout.findViewById(R.id.change );
                    symbolTextView.setText(data.getString(0));
                    bidTextView.setText(bid);
                    changeTextView.setText(data.getString(2));

                    graphLinearLayout.addView(listItemQuoteLinearLayout,0);
                }
            }
            dataset.setDotsRadius(7);
            dataset.setDotsStrokeColor(getResources().getColor(R.color.material_green_700));

            Paint paint = new Paint();
            paint.setColor(Color.parseColor("#717171"));

            dataset.setDotsColor(Color.WHITE);
            dataset.setColor(getResources().getColor(R.color.material_green_700));

            lineChartView.dismiss();
            lineChartView.addData(dataset);
            lineChartView.setAxisBorderValues((int) min - 1, (int) max + 1);
            lineChartView.setAxisColor(Color.WHITE);
            lineChartView.setLabelsColor(Color.WHITE);
            lineChartView.setStep(1);
            lineChartView.setGrid(ChartView.GridType.FULL, paint);
            lineChartView.show();
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

    }
}
