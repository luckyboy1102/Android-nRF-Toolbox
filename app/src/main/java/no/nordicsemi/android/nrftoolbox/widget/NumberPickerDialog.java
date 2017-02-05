package no.nordicsemi.android.nrftoolbox.widget;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.NumberPicker;

import no.nordicsemi.android.nrftoolbox.R;

/**
 * Created by Chen on 2017/2/5.
 */

public class NumberPickerDialog extends AlertDialog implements DialogInterface.OnClickListener {

    private final OnNumberSetListener mCallback;
    private final NumberPicker mNumberPicker;

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == BUTTON_POSITIVE) {
            if (mCallback != null) {
                mCallback.onNumberSet(mNumberPicker.getValue());
            }
        }
    }

    public interface OnNumberSetListener {
        void onNumberSet(int number);
    }

    public NumberPickerDialog(Context context, OnNumberSetListener callback) {
        this(context, 0, callback);
    }

    public NumberPickerDialog(Context context, int themeResId, OnNumberSetListener callback) {
        super(context, themeResId);

        mCallback = callback;

        setTitle("Select number");

        Context themeContext = getContext();
        setButton(BUTTON_POSITIVE, themeContext.getText(R.string.ok), this);
        setButton(BUTTON_NEGATIVE, themeContext.getText(R.string.cancel), this);

        LayoutInflater inflater = (LayoutInflater) themeContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.dialog_number_picker, null);
        setView(view);

        mNumberPicker = (NumberPicker) view.findViewById(R.id.number_picker);
        mNumberPicker.setMinValue(1);
        mNumberPicker.setMaxValue(60);
    }
}
