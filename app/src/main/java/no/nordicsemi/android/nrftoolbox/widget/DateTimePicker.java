package no.nordicsemi.android.nrftoolbox.widget;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.DatePicker;
import android.widget.TimePicker;

import java.util.Calendar;
import java.util.Date;

import no.nordicsemi.android.nrftoolbox.R;

public class DateTimePicker extends AlertDialog implements OnClickListener {

    private final OnDateSetListener mCallBack;
    private final DatePicker mDatePicker;
    private final TimePicker mTimePicker;

    private final Calendar mCalendar;

    public interface OnDateSetListener {

        void onDateSet(Date date);
    }

    public DateTimePicker(Context context, OnDateSetListener callback) {
        this(context, 0, callback);
    }

    public DateTimePicker(Context context, int theme, OnDateSetListener callback) {
        super(context, theme);

        mCallBack = callback;

        mCalendar = Calendar.getInstance();

        setTitle("Select date & time");

        Context themeContext = getContext();
        setButton(BUTTON_POSITIVE, themeContext.getText(R.string.ok), this);
        setButton(BUTTON_NEGATIVE, themeContext.getText(R.string.cancel), this);

        LayoutInflater inflater = (LayoutInflater) themeContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.dialog_date_time, null);
        setView(view);

        mDatePicker = (DatePicker) view.findViewById(R.id.date_picker);
        mTimePicker = (TimePicker) view.findViewById(R.id.time_picker);
        mTimePicker.setIs24HourView(true);
        mTimePicker.setCurrentHour(mCalendar.get(Calendar.HOUR_OF_DAY));
        mTimePicker.setCurrentMinute(mCalendar.get(Calendar.MINUTE));

        mDatePicker.init(mCalendar.get(Calendar.YEAR), mCalendar.get(Calendar.MONTH), mCalendar.get(Calendar.DAY_OF_MONTH), null);


    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case BUTTON_POSITIVE:
                if (mCallBack != null) {
                    mCalendar.set(mDatePicker.getYear(), mDatePicker.getMonth(), mDatePicker.getDayOfMonth(),
                            mTimePicker.getCurrentHour(), mTimePicker.getCurrentMinute());

                    mCallBack.onDateSet(mCalendar.getTime());
                }
                break;
        }
    }


}
