/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package no.nordicsemi.android.nrftoolbox.bpm;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

import no.nordicsemi.android.log.Logger;
import no.nordicsemi.android.nrftoolbox.parser.BloodPressureMeasurementParser;
import no.nordicsemi.android.nrftoolbox.parser.IntermediateCuffPressureParser;
import no.nordicsemi.android.nrftoolbox.profile.BleManager;
import no.nordicsemi.android.nrftoolbox.utility.DebugLogger;
import no.nordicsemi.android.nrftoolbox.utility.ParserUtils;

public class BPMManager extends BleManager<BPMManagerCallbacks> {
	private static final String TAG = "BPMManager";

	/** Blood Pressure service UUID */
	public final static UUID BP_SERVICE_UUID = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb");
	/** Blood Pressure Measurement characteristic UUID */
	private static final UUID BPM_CHARACTERISTIC_UUID = UUID.fromString("00002A35-0000-1000-8000-00805f9b34fb");
	/** Intermediate Cuff Pressure characteristic UUID */
	private static final UUID ICP_CHARACTERISTIC_UUID = UUID.fromString("00002A36-0000-1000-8000-00805f9b34fb");
	/** Record Access Control Point characteristic UUID */
	private final static UUID RACP_CHARACTERISTIC = UUID.fromString("00002A52-0000-1000-8000-00805f9b34fb");

	private static final UUID CURRENT_TIME = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb");

	private static final UUID CURRENT_TIME_CHARACTERISTIC = UUID.fromString("00002A2B-0000-1000-8000-00805f9b34fb");

	private BluetoothGattCharacteristic mBPMCharacteristic, mICPCharacteristic, mRecordAccessControlPointCharacteristic;
	private BluetoothGattCharacteristic mCurrentTimeCharacteristic;

	private static BPMManager managerInstance = null;

	private final static int OP_CODE_REPORT_STORED_RECORDS = 1;
	private final static int OP_CODE_DELETE_STORED_RECORDS = 2;
	private final static int OP_CODE_ABORT_OPERATION = 3;
	private final static int OP_CODE_REPORT_NUMBER_OF_RECORDS = 4;
	private final static int OP_CODE_NUMBER_OF_STORED_RECORDS_RESPONSE = 5;
	private final static int OP_CODE_RESPONSE_CODE = 6;

	private final static int OPERATOR_NULL = 0;
	private final static int OPERATOR_ALL_RECORDS = 1;
	private final static int OPERATOR_LESS_THEN_OR_EQUAL = 2;
	private final static int OPERATOR_GREATER_THEN_OR_EQUAL = 3;
	private final static int OPERATOR_WITHING_RANGE = 4;
	private final static int OPERATOR_FIRST_RECORD = 5;
	private final static int OPERATOR_LAST_RECORD = 6;

	private final static int RESPONSE_SUCCESS = 1;
	private final static int RESPONSE_OP_CODE_NOT_SUPPORTED = 2;
	private final static int RESPONSE_INVALID_OPERATOR = 3;
	private final static int RESPONSE_OPERATOR_NOT_SUPPORTED = 4;
	private final static int RESPONSE_INVALID_OPERAND = 5;
	private final static int RESPONSE_NO_RECORDS_FOUND = 6;
	private final static int RESPONSE_ABORT_UNSUCCESSFUL = 7;
	private final static int RESPONSE_PROCEDURE_NOT_COMPLETED = 8;
	private final static int RESPONSE_OPERAND_NOT_SUPPORTED = 9;

	/**
	 * The filter type is used for range operators ({@link #OPERATOR_LESS_THEN_OR_EQUAL}, {@link #OPERATOR_GREATER_THEN_OR_EQUAL}, {@link #OPERATOR_WITHING_RANGE}.<br/>
	 * The syntax of the operand is: [Filter Type][Minimum][Maximum].<br/>
	 * This filter selects the records by the sequence number.
	 */
	private final static int FILTER_TYPE_SEQUENCE_NUMBER = 1;
	/**
	 * The filter type is used for range operators ({@link #OPERATOR_LESS_THEN_OR_EQUAL}, {@link #OPERATOR_GREATER_THEN_OR_EQUAL}, {@link #OPERATOR_WITHING_RANGE}.<br/>
	 * The syntax of the operand is: [Filter Type][Minimum][Maximum].<br/>
	 * This filter selects the records by the user facing time (base time + offset time).
	 */
	private final static int FILTER_TYPE_USER_FACING_TIME = 2;

	private final LinkedList<BPMRecord> mRecords = new LinkedList<>();
	private boolean mAbort;
	private Handler mHandler;

	/**
	 * Returns the singleton implementation of BPMManager
	 */
	public static synchronized BPMManager getBPMManager(final Context context) {
		if (managerInstance == null) {
			managerInstance = new BPMManager(context);
		}
		return managerInstance;
	}

	private BPMManager(final Context context) {
		super(context);
		mHandler = new Handler();
	}

	@Override
	protected BleManagerGattCallback getGattCallback() {
		return mGattCallback;
	}

	/**
	 * BluetoothGatt callbacks for connection/disconnection, service discovery, receiving notification, etc
	 */
	private final BleManagerGattCallback  mGattCallback = new BleManagerGattCallback() {

		@Override
		protected Queue<Request> initGatt(final BluetoothGatt gatt) {
			final LinkedList<Request> requests = new LinkedList<>();
			if (mICPCharacteristic != null)
				requests.push(Request.newEnableNotificationsRequest(mICPCharacteristic));
			requests.push(Request.newEnableIndicationsRequest(mBPMCharacteristic));
			if (mRecordAccessControlPointCharacteristic != null) {
				requests.push(Request.newEnableIndicationsRequest(mRecordAccessControlPointCharacteristic));
			}
			if (mCurrentTimeCharacteristic != null) {
				requests.push(setTime());
			}
			return requests;
		}

		@Override
		protected boolean isRequiredServiceSupported(final BluetoothGatt gatt) {
			BluetoothGattService service = gatt.getService(BP_SERVICE_UUID);
			if (service != null) {
				mBPMCharacteristic = service.getCharacteristic(BPM_CHARACTERISTIC_UUID);
				mICPCharacteristic = service.getCharacteristic(ICP_CHARACTERISTIC_UUID);
				mRecordAccessControlPointCharacteristic = service.getCharacteristic(RACP_CHARACTERISTIC);
			}
			BluetoothGattService mCurrentTimeService = gatt.getService(CURRENT_TIME);
			if (mCurrentTimeService != null) {
				mCurrentTimeCharacteristic = mCurrentTimeService.getCharacteristic(CURRENT_TIME_CHARACTERISTIC);
			}
			return mBPMCharacteristic != null;
		}

		@Override
		protected boolean isOptionalServiceSupported(final BluetoothGatt gatt) {
			return mICPCharacteristic != null;
		}

		@Override
		protected void onDeviceDisconnected() {
			mICPCharacteristic = null;
			mBPMCharacteristic = null;
			mRecordAccessControlPointCharacteristic = null;
		}

		@Override
		protected void onCharacteristicNotified(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
			// Intermediate Cuff Pressure characteristic read
			if (mLogSession != null)
				Logger.a(mLogSession, IntermediateCuffPressureParser.parse(characteristic));

			parseBPMValue(characteristic);
		}

		@Override
		protected void onCharacteristicIndicated(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
			// Blood Pressure Measurement characteristic read
			if (mLogSession != null)
				Logger.a(mLogSession, BloodPressureMeasurementParser.parse(characteristic));

			if (characteristic.getUuid().equals(BPM_CHARACTERISTIC_UUID)) {
				parseBPMValue(characteristic);
			}

			if (characteristic.getUuid().equals(RACP_CHARACTERISTIC)) {
				processedRacp(characteristic);
			}
		}

		@Override
		protected void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
			super.onCharacteristicWrite(gatt, characteristic);
			Log.d(TAG, ParserUtils.parse(characteristic));
		}

		private void parseBPMValue(final BluetoothGattCharacteristic characteristic) {
			// Both BPM and ICP have the same structure.

			// first byte - flags
			int offset = 0;
			final int flags = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset++);
			// See BPMManagerCallbacks.UNIT_* for unit options
			final int unit = flags & 0x01;
			final boolean timestampPresent = (flags & 0x02) > 0;
			final boolean pulseRatePresent = (flags & 0x04) > 0;

			final BPMRecord record = new BPMRecord();

			if (BPM_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
				// following bytes - systolic, diastolic and mean arterial pressure
				final float systolic = characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_SFLOAT, offset);
				final float diastolic = characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_SFLOAT, offset + 2);
				final float meanArterialPressure = characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_SFLOAT, offset + 4);
				offset += 6;

				record.systolic = systolic;
				record.diastolic = diastolic;
				mCallbacks.onBloodPressureMeasurementRead(systolic, diastolic, meanArterialPressure, unit);
			} else if (ICP_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
				// following bytes - cuff pressure. Diastolic and MAP are unused
				final float cuffPressure = characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_SFLOAT, offset);
				offset += 6;
				mCallbacks.onIntermediateCuffPressureRead(cuffPressure, unit);
			}

			// parse timestamp if present
			if (timestampPresent) {
				final Calendar calendar = Calendar.getInstance();
				calendar.set(Calendar.YEAR, characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset));
				calendar.set(Calendar.MONTH, characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset + 2) - 1); // months are 1-based
				calendar.set(Calendar.DAY_OF_MONTH, characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset + 3));
				calendar.set(Calendar.HOUR_OF_DAY, characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset + 4));
				calendar.set(Calendar.MINUTE, characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset + 5));
				calendar.set(Calendar.SECOND, characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset + 6));
				offset += 7;

				record.time = calendar;
				mCallbacks.onTimestampRead(calendar);
			} else
				mCallbacks.onTimestampRead(null);

			// parse pulse rate if present
			if (pulseRatePresent) {
				final float pulseRate = characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_SFLOAT, offset);
				// offset += 2;
				record.pulseRate = pulseRate;
				mCallbacks.onPulseRateRead(pulseRate);
			} else
				mCallbacks.onPulseRateRead(-1.0f);

			if (BPM_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
				mRecords.add(record);
				mCallbacks.onDatasetChanged();
			}
		}

		private void processedRacp(BluetoothGattCharacteristic characteristic) {
			// Record Access Control Point characteristic
			int offset = 0;
			final int opCode = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset);
			offset += 2; // skip the operator

			if (opCode == OP_CODE_NUMBER_OF_STORED_RECORDS_RESPONSE) {
				// We've obtained the number of all records
				final int number = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);

				mCallbacks.onNumberOfRecordsRequested(number);

				// Request the records
				if (number > 0) {
					final BluetoothGattCharacteristic racpCharacteristic = mRecordAccessControlPointCharacteristic;
					setOpCode(racpCharacteristic, OP_CODE_REPORT_STORED_RECORDS, OPERATOR_ALL_RECORDS);
					writeCharacteristic(racpCharacteristic);
				} else {
					mCallbacks.onOperationCompleted();
				}
			} else if (opCode == OP_CODE_RESPONSE_CODE) {
				final int requestedOpCode = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset);
				final int responseCode = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset + 1);
				DebugLogger.d(TAG, "Response result for: " + requestedOpCode + " is: " + responseCode);

				switch (responseCode) {
					case RESPONSE_SUCCESS:
						if (!mAbort)
							mCallbacks.onOperationCompleted();
						else
							mCallbacks.onOperationAborted();
						break;
					case RESPONSE_NO_RECORDS_FOUND:
						mCallbacks.onOperationCompleted();
						break;
					case RESPONSE_OP_CODE_NOT_SUPPORTED:
						mCallbacks.onOperationNotSupported();
						break;
					case RESPONSE_PROCEDURE_NOT_COMPLETED:
					case RESPONSE_ABORT_UNSUCCESSFUL:
					default:
						mCallbacks.onOperationFailed();
						break;
				}
				mAbort = false;
			}
		}
	};

	/**
	 * Clears the records list locally
	 */
	public void clear() {
		mRecords.clear();
		mCallbacks.onDatasetChanged();
	}

	/**
	 * Returns all records as a sparse array where sequence number is the key.
	 *
	 * @return the records list
	 */
	public LinkedList<BPMRecord> getRecords() {
		return mRecords;
	}

	/**
	 * Sends the request to obtain the last (most recent) record from glucose device. The data will be returned to Glucose Measurement characteristic as a notification followed by Record Access
	 * Control Point indication with status code ({@link #RESPONSE_SUCCESS} or other in case of error.
	 */
	public void getLastRecord() {
		if (mRecordAccessControlPointCharacteristic == null)
			return;

		clear();
		mCallbacks.onOperationStarted();

		final BluetoothGattCharacteristic characteristic = mRecordAccessControlPointCharacteristic;
		setOpCode(characteristic, OP_CODE_REPORT_STORED_RECORDS, OPERATOR_LAST_RECORD);
		writeCharacteristic(characteristic);
	}

	/**
	 * Sends the request to obtain all records from glucose device. Initially we want to notify him/her about the number of the records so the {@link #OP_CODE_REPORT_NUMBER_OF_RECORDS} is send. The
	 * data will be returned to Glucose Measurement characteristic as a notification followed by Record Access Control Point indication with status code ({@link #RESPONSE_SUCCESS} or other in case of
	 * error.
	 */
	public void getAllRecords() {
		if (mRecordAccessControlPointCharacteristic == null)
			return;

		clear();
		mCallbacks.onOperationStarted();

		final BluetoothGattCharacteristic characteristic = mRecordAccessControlPointCharacteristic;
		setOpCode(characteristic, OP_CODE_REPORT_STORED_RECORDS, OPERATOR_ALL_RECORDS);
		writeCharacteristic(characteristic);
	}

	/**
	 * Sends abort operation signal to the device
	 */
	public void abort() {
		if (mRecordAccessControlPointCharacteristic == null)
			return;

		mAbort = true;
		final BluetoothGattCharacteristic characteristic = mRecordAccessControlPointCharacteristic;
		setOpCode(characteristic, OP_CODE_ABORT_OPERATION, OPERATOR_NULL);
		writeCharacteristic(characteristic);
	}

	/**
	 * Sends the request to obtain from the glucose device all records newer than the newest one from local storage. The data will be returned to Glucose Measurement characteristic as a notification
	 * followed by Record Access Control Point indication with status code ({@link #RESPONSE_SUCCESS} or other in case of error.
	 * <p>
	 * Refresh button will not download records older than the oldest in the local memory. F.e. if you have pressed Last and then Refresh, than it will try to get only newer records. However if there
	 * are no records, it will download all existing (using {@link #getAllRecords()}).
	 * </p>
	 */
	public void refreshRecords() {
		if (mRecordAccessControlPointCharacteristic == null)
			return;

		if (mRecords.size() == 0) {
			getAllRecords();
		} else {
//			mCallbacks.onOperationStarted();
//
//			// obtain the last sequence number
//			final int sequenceNumber = mRecords.keyAt(mRecords.size() - 1) + 1;
//
//			final BluetoothGattCharacteristic characteristic = mRecordAccessControlPointCharacteristic;
//			setOpCode(characteristic, OP_CODE_REPORT_STORED_RECORDS, OPERATOR_GREATER_THEN_OR_EQUAL, sequenceNumber);
//			writeCharacteristic(characteristic);
			// Info:
			// Operators OPERATOR_LESS_THEN_OR_EQUAL and OPERATOR_RANGE are not supported by Nordic Semiconductor Glucose Service in SDK 4.4.2.
		}
	}

	/**
	 * Sends the request to obtain the first (oldest) record from glucose device. The data will be returned to Glucose Measurement characteristic as a notification followed by Record Access Control
	 * Point indication with status code ({@link #RESPONSE_SUCCESS} or other in case of error.
	 */
	public void getFirstRecord() {
		if (mRecordAccessControlPointCharacteristic == null)
			return;

		clear();
		mCallbacks.onOperationStarted();

		final BluetoothGattCharacteristic characteristic = mRecordAccessControlPointCharacteristic;
		setOpCode(characteristic, OP_CODE_REPORT_STORED_RECORDS, OPERATOR_FIRST_RECORD);
		writeCharacteristic(characteristic);
	}

	/**
	 * Sends the request to delete all data from the device. A Record Access Control Point indication with status code ({@link #RESPONSE_SUCCESS} (or other in case of error) will be send.
	 *
	 * @FIXME This method is not supported by Nordic Semiconductor Glucose Service in SDK 4.4.2.
	 */
	public void deleteAllRecords() {
		if (mRecordAccessControlPointCharacteristic == null)
			return;

		clear();
		mCallbacks.onOperationStarted();

		final BluetoothGattCharacteristic characteristic = mRecordAccessControlPointCharacteristic;
		setOpCode(characteristic, OP_CODE_DELETE_STORED_RECORDS, OPERATOR_ALL_RECORDS);
		writeCharacteristic(characteristic);
	}

	public void getSpecificRecord(Date date, boolean greater) {
		if (mRecordAccessControlPointCharacteristic == null)
			return;

		clear();
		mCallbacks.onOperationStarted();

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		int year = calendar.get(Calendar.YEAR);
		int month = calendar.get(Calendar.MONTH) + 1;
		int day = calendar.get(Calendar.DAY_OF_MONTH);
		int hour = calendar.get(Calendar.HOUR_OF_DAY);
		int min = calendar.get(Calendar.MINUTE);

		String strHexYear = "0" + Integer.toHexString(year);

		Integer[] array = new Integer[7];
		array[0] = Integer.parseInt(strHexYear.substring(2), 16);
		array[1] = Integer.parseInt(strHexYear.substring(0, 2), 16);
		array[2] = Integer.parseInt(Integer.toHexString(month), 16);
		array[3] = Integer.parseInt(Integer.toHexString(day), 16);
		array[4] = Integer.parseInt(Integer.toHexString(hour), 16);
		array[5] = Integer.parseInt(Integer.toHexString(min), 16);
		array[6] = Integer.parseInt(Integer.toHexString(0), 16);

		final BluetoothGattCharacteristic characteristic = mRecordAccessControlPointCharacteristic;
		setOpCodeFilter(characteristic, OP_CODE_REPORT_STORED_RECORDS, greater ? OPERATOR_GREATER_THEN_OR_EQUAL : OPERATOR_LESS_THEN_OR_EQUAL, array);
		writeCharacteristic(characteristic);
	}

	/**
	 * Writes given operation parameters to the characteristic
	 *
	 * @param characteristic
	 *            the characteristic to write. This must be the Record Access Control Point characteristic
	 * @param opCode
	 *            the operation code
	 * @param operator
	 *            the operator (see {@link #OPERATOR_NULL} and others
	 * @param params
	 *            optional parameters (one for >=, <=, two for the range, none for other operators)
	 */
	private void setOpCode(final BluetoothGattCharacteristic characteristic, final int opCode, final int operator, final Integer... params) {
		final int size = 2 + ((params.length > 0) ? 1 : 0) + params.length * 2; // 1 byte for opCode, 1 for operator, 1 for filter type (if parameters exists) and 2 for each parameter
		characteristic.setValue(new byte[size]);

		// write the operation code
		int offset = 0;
		characteristic.setValue(opCode, BluetoothGattCharacteristic.FORMAT_UINT8, offset);
		offset += 1;

		// write the operator. This is always present but may be equal to OPERATOR_NULL
		characteristic.setValue(operator, BluetoothGattCharacteristic.FORMAT_UINT8, offset);
		offset += 1;

		// if parameters exists, append them. Parameters should be sorted from minimum to maximum. Currently only one or two params are allowed
		if (params.length > 0) {
			// our implementation use only sequence number as a filer type
			characteristic.setValue(FILTER_TYPE_SEQUENCE_NUMBER, BluetoothGattCharacteristic.FORMAT_UINT8, offset);
			offset += 1;

			for (final Integer i : params) {
				characteristic.setValue(i, BluetoothGattCharacteristic.FORMAT_UINT16, offset);
				offset += 2;
			}
		}
	}


	private void setOpCodeFilter(final BluetoothGattCharacteristic characteristic, final int opCode, final int operator, final Integer... params) {
		final int size = 2 + ((params.length > 0) ? 1 : 0) + params.length; // 1 byte for opCode, 1 for operator, 1 for filter type (if parameters exists) and 2 for each parameter
		characteristic.setValue(new byte[size]);

		// write the operation code
		int offset = 0;
		characteristic.setValue(opCode, BluetoothGattCharacteristic.FORMAT_UINT8, offset);
		offset += 1;

		// write the operator. This is always present but may be equal to OPERATOR_NULL
		characteristic.setValue(operator, BluetoothGattCharacteristic.FORMAT_UINT8, offset);
		offset += 1;

		// if parameters exists, append them. Parameters should be sorted from minimum to maximum. Currently only one or two params are allowed
		if (params.length > 0) {
			// our implementation use only sequence number as a filer type
			characteristic.setValue(FILTER_TYPE_USER_FACING_TIME, BluetoothGattCharacteristic.FORMAT_UINT8, offset);
			offset += 1;

			for (final Integer i : params) {
				characteristic.setValue(i, BluetoothGattCharacteristic.FORMAT_UINT8, offset);
				offset += 1;
			}
		}
	}

	private Request setTime() {
		Calendar calendar = Calendar.getInstance();
		int year = calendar.get(Calendar.YEAR);
		int month = calendar.get(Calendar.MONTH) + 1;
		int day = calendar.get(Calendar.DAY_OF_MONTH);
		int hour = calendar.get(Calendar.HOUR_OF_DAY);
		int min = calendar.get(Calendar.MINUTE);

		String strHexYear = "0" + Integer.toHexString(year);

		byte[] array = new byte[10];
		array[0] = (byte) Integer.parseInt(strHexYear.substring(2), 16);
		array[1] = (byte) Integer.parseInt(strHexYear.substring(0, 2), 16);
		array[2] = (byte) Integer.parseInt(Integer.toHexString(month), 16);
		array[3] = (byte) Integer.parseInt(Integer.toHexString(day), 16);
		array[4] = (byte) Integer.parseInt(Integer.toHexString(hour), 16);
		array[5] = (byte) Integer.parseInt(Integer.toHexString(min), 16);
		array[6] = (byte) Integer.parseInt(Integer.toHexString(0), 16);
		array[7] = (byte) Integer.parseInt(Integer.toHexString(0), 16);
		array[8] = (byte) Integer.parseInt(Integer.toHexString(0), 16);
		array[9] = (byte) Integer.parseInt(Integer.toHexString(0), 16);

		return Request.newWriteRequest(mCurrentTimeCharacteristic, array);
	}
}
