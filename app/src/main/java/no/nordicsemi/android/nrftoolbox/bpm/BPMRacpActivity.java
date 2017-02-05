package no.nordicsemi.android.nrftoolbox.bpm;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

import no.nordicsemi.android.nrftoolbox.R;
import no.nordicsemi.android.nrftoolbox.profile.BleManager;
import no.nordicsemi.android.nrftoolbox.profile.BleManagerCallbacks;
import no.nordicsemi.android.nrftoolbox.profile.BleProfileActivity;
import no.nordicsemi.android.nrftoolbox.widget.DateTimePicker;
import no.nordicsemi.android.nrftoolbox.widget.NumberPickerDialog;

/**
 * Created by Chen on 2016/2/23.
 */
public class BPMRacpActivity extends BleProfileActivity implements PopupMenu.OnMenuItemClickListener, BPMManagerCallbacks {

    private static final String TAG = "BPMRacpActivity";

    private BPMRecordsAdapter mAdapter;
    private BPMManager mBPMManager;

    private View mControlPanelStd;
    private View mControlPanelAbort;

    @Override
    protected void onCreateView(Bundle savedInstanceState) {
        setContentView(R.layout.activity_feature_bpm_racp);
        setGUI();
    }

    @Override
    protected BleManager<? extends BleManagerCallbacks> initializeManager() {
        final BPMManager manager = mBPMManager = BPMManager.getBPMManager(getApplicationContext());
        manager.setGattCallbacks(this);
        return manager;
    }

    @Override
    protected void setDefaultUI() {
        mBPMManager.clear();
    }

    @Override
    protected int getDefaultDeviceName() {
        return R.string.bpm_default_name;
    }

    @Override
    protected int getAboutTextId() {
        return R.string.bpm_about_text;
    }

    @Override
    protected UUID getFilterUUID() {
        return BPMManager.BP_SERVICE_UUID;
    }

    private void setGUI() {
        mControlPanelStd = findViewById(R.id.gls_control_std);
        mControlPanelAbort = findViewById(R.id.gls_control_abort);

        findViewById(R.id.action_last).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBPMManager.getLastRecord();
            }
        });
        findViewById(R.id.action_all).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBPMManager.getAllRecords();
            }
        });
        findViewById(R.id.action_abort).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBPMManager.abort();
            }
        });

        // create popup menu attached to the button More
        findViewById(R.id.action_more).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PopupMenu menu = new PopupMenu(BPMRacpActivity.this, v);
                menu.setOnMenuItemClickListener(BPMRacpActivity.this);
                MenuInflater inflater = menu.getMenuInflater();
                inflater.inflate(R.menu.gls_more, menu.getMenu());
                menu.show();
            }
        });

        ListView listView = (ListView) findViewById(R.id.list);
        listView.setAdapter(mAdapter = new BPMRecordsAdapter());


    }

    @Override
    public void onServicesDiscovered(boolean optionalServicesFound) {

    }

    @Override
    public void onDeviceReady() {

    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                showToast("Same as all");
                break;
            case R.id.action_first:
                mBPMManager.getFirstRecord();
                break;
            case R.id.action_clear:
                mBPMManager.clear();
                break;
            case R.id.action_delete_all:
                mBPMManager.deleteAllRecords();
                break;
            case R.id.action_greater_than:
                new DateTimePicker(this, new DateTimePicker.OnDateSetListener() {
                    @Override
                    public void onDateSet(Date date) {
                        mBPMManager.getSpecificRecord(date, true);
                    }
                }).show();
                break;
            case R.id.action_less_than:
                new DateTimePicker(this, new DateTimePicker.OnDateSetListener() {
                    @Override
                    public void onDateSet(Date date) {
                        mBPMManager.getSpecificRecord(date, false);
                    }
                }).show();
                break;
            case R.id.action_pause:
                mBPMManager.abort();
                break;
            case R.id.action_greater_than_seq:
                new NumberPickerDialog(this, new NumberPickerDialog.OnNumberSetListener() {
                    @Override
                    public void onNumberSet(int number) {
                        mBPMManager.getRecordsBySequence(number, true);
                    }
                }).show();
                break;
            case R.id.action_less_than_seq:
                new NumberPickerDialog(this, new NumberPickerDialog.OnNumberSetListener() {
                    @Override
                    public void onNumberSet(int number) {
                        mBPMManager.getRecordsBySequence(number, false);
                    }
                }).show();
                break;
        }
        return true;
    }

    private void setOperationInProgress(final boolean progress) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // setSupportProgressBarIndeterminateVisibility(progress);
                mControlPanelStd.setVisibility(!progress ? View.VISIBLE : View.GONE);
                mControlPanelAbort.setVisibility(progress ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    public void onBloodPressureMeasurementRead(float systolic, float diastolic, float meanArterialPressure, int unit) {

    }

    @Override
    public void onIntermediateCuffPressureRead(float cuffPressure, int unit) {

    }

    @Override
    public void onPulseRateRead(float pulseRate) {

    }

    @Override
    public void onTimestampRead(Calendar calendar) {

    }

    @Override
    public void onDatasetChanged() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onOperationStarted() {
//        setOperationInProgress(true);
    }

    @Override
    public void onOperationCompleted() {
//        setOperationInProgress(false);
    }

    @Override
    public void onOperationFailed() {
        setOperationInProgress(false);
        showToast(R.string.gls_operation_failed);
    }

    @Override
    public void onOperationAborted() {
        setOperationInProgress(false);
    }

    @Override
    public void onOperationNotSupported() {
        setOperationInProgress(false);
        showToast(R.string.gls_operation_not_supported);
    }

    @Override
    public void onNumberOfRecordsRequested(int value) {
        showToast(getString(R.string.gls_progress, value));
    }

    public class BPMRecordsAdapter extends BaseAdapter {

        LayoutInflater inflater;
        SimpleDateFormat sdf;

        public BPMRecordsAdapter() {
            inflater = getLayoutInflater();
            sdf = new SimpleDateFormat("yyyy-MM-dd \nHH:mm:ss");
        }

        @Override
        public int getCount() {
            return mBPMManager.getRecords().size();
        }

        @Override
        public BPMRecord getItem(int position) {
            return mBPMManager.getRecords().get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            BPMViewHolder holder;

            if (convertView == null) {
                convertView = inflater.inflate(R.layout.activity_feature_bpm_item, null);
                holder = new BPMViewHolder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (BPMViewHolder) convertView.getTag();
            }

            BPMRecord record = getItem(position);
            holder.time.setText(sdf.format(record.time.getTime()));
            holder.details.setText(record.systolic + " / " + record.diastolic + " \n"
                    + record.pulseRate);

            return convertView;
        }
    }

    private class BPMViewHolder {
        private TextView time;
        private TextView details;

        public BPMViewHolder(View view) {
            time = (TextView) view.findViewById(R.id.time);
            details = (TextView) view.findViewById(R.id.details);
        }
    }
}
