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

package no.nordicsemi.android.nrftoolbox.csc;

import java.util.List;
import java.util.UUID;

import no.nordicsemi.android.log.ILogSession;
import no.nordicsemi.android.log.Logger;
import no.nordicsemi.android.nrftoolbox.profile.BleManager;
import no.nordicsemi.android.nrftoolbox.utility.DebugLogger;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class CSCManager implements BleManager<CSCManagerCallbacks> {
	private static final String TAG = "RSCManager";

	private CSCManagerCallbacks mCallbacks;
	private BluetoothGatt mBluetoothGatt;
	private Context mContext;
	private ILogSession mLogSession;

	private static final byte WHEEL_REVOLUTIONS_DATA_PRESENT = 0x01; // 1 bit
	private static final byte CRANK_REVOLUTION_DATA_PRESENT = 0x02; // 1 bit

	public final static UUID CYCLING_SPEED_AND_CADENCE_SERVICE_UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb");
	/** Cycling Speed and Cadence Measurement characteristic */
	private static final UUID CSC_MEASUREMENT_CHARACTERISTIC_UUID = UUID.fromString("00002A5B-0000-1000-8000-00805f9b34fb");

	private final static UUID BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb");
	/** Battery Level characteristic */
	private final static UUID BATTERY_LEVEL_CHARACTERISTIC_UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb");
	/** Client configuration descriptor that will allow us to enable notifications and indications */
	private static final UUID CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

	private final static String ERROR_CONNECTION_STATE_CHANGE = "Error on connection state change";
	private final static String ERROR_DISCOVERY_SERVICE = "Error on discovering services";
	private final static String ERROR_AUTH_ERROR_WHILE_BONDED = "Phone has lost bonding information";
	private final static String ERROR_WRITE_DESCRIPTOR = "Error on writing descriptor";
	private final static String ERROR_READ_CHARACTERISTIC = "Error on reading characteristic";

	private BluetoothGattCharacteristic mCSCMeasurementCharacteristic, mBatteryCharacteristic;
	private boolean mBatteryLevelNotificationsEnabled;

	public CSCManager(final Context context) {
		// Register bonding broadcast receiver
		final IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
		context.registerReceiver(mBondingBroadcastReceiver, filter);
	}

	@Override
	public void setGattCallbacks(final CSCManagerCallbacks callbacks) {
		mCallbacks = callbacks;
	}

	public void setLogger(final ILogSession session) {
		mLogSession = session;
	}

	@Override
	public void connect(final Context context, final BluetoothDevice device) {
		mContext = context;

		Logger.i(mLogSession, "[CSC] Gatt server started");
		if (mBluetoothGatt == null) {
			mBluetoothGatt = device.connectGatt(mContext, true, mGattCallback);
		} else {
			mBluetoothGatt.connect();
		}
	}

	@Override
	public void disconnect() {
		if (mBluetoothGatt != null) {
			mBluetoothGatt.disconnect();
		}
	}

	/**
	 * BluetoothGatt callbacks for connection/disconnection, service discovery, receiving indication, etc
	 */
	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				if (newState == BluetoothProfile.STATE_CONNECTED) {
					DebugLogger.d(TAG, "Device connected");
					mBluetoothGatt.discoverServices();
					//This will send callback to RSCActivity when device get connected
					mCallbacks.onDeviceConnected();
				} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
					DebugLogger.d(TAG, "Device disconnected");

					// TODO It should check whether the user has requested disconnection or was it link loss. On Samsung S4 the DevKit reconnects itself just after linkloss but the Service is already dead.
					mCallbacks.onDeviceDisconnected();
					closeBluetoothGatt();
				}
			} else {
				mCallbacks.onError(ERROR_CONNECTION_STATE_CHANGE, status);
			}
		}

		@Override
		public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				final List<BluetoothGattService> services = gatt.getServices();
				for (BluetoothGattService service : services) {
					if (service.getUuid().equals(CYCLING_SPEED_AND_CADENCE_SERVICE_UUID)) {
						DebugLogger.d(TAG, "Cycling Speed and Cadence service is found");
						mCSCMeasurementCharacteristic = service.getCharacteristic(CSC_MEASUREMENT_CHARACTERISTIC_UUID);
					} else if (service.getUuid().equals(BATTERY_SERVICE_UUID)) {
						DebugLogger.d(TAG, "Battery service is found");
						mBatteryCharacteristic = service.getCharacteristic(BATTERY_LEVEL_CHARACTERISTIC_UUID);
					}
				}
				if (mCSCMeasurementCharacteristic == null) {
					mCallbacks.onDeviceNotSupported();
					gatt.disconnect();
				} else {
					mCallbacks.onServicesDiscovered(false /* more characteristics not supported */);

					// We have discovered services, let's start notifications and indications, one by one: battery, csc measurement
					if (mBatteryCharacteristic != null) {
						// Some devices has Battery Level characteristic without READ property
						if ((mBatteryCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) > 0)
							readBatteryLevel(gatt);
						else if ((mBatteryCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0)
							enableBatteryLevelNotification(gatt);
						else
							enableCSCMeasurementNotification(gatt);
					} else {
						enableCSCMeasurementNotification(gatt);
					}
				}
			} else {
				mCallbacks.onError(ERROR_DISCOVERY_SERVICE, status);
			}
		}

		@Override
		public void onCharacteristicRead(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				if (characteristic.getUuid().equals(BATTERY_LEVEL_CHARACTERISTIC_UUID)) {
					int batteryValue = characteristic.getValue()[0];
					mCallbacks.onBatteryValueReceived(batteryValue);

					if (!mBatteryLevelNotificationsEnabled)
						enableCSCMeasurementNotification(gatt);
				}
			} else if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) {
				if (gatt.getDevice().getBondState() != BluetoothDevice.BOND_NONE) {
					DebugLogger.w(TAG, ERROR_AUTH_ERROR_WHILE_BONDED);
					mCallbacks.onError(ERROR_AUTH_ERROR_WHILE_BONDED, status);
				}
			} else {
				mCallbacks.onError(ERROR_READ_CHARACTERISTIC, status);
			}
		}

		@Override
		public void onDescriptorWrite(final BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				if (descriptor.getCharacteristic().getUuid().equals(BATTERY_LEVEL_CHARACTERISTIC_UUID)) {
					enableCSCMeasurementNotification(gatt);
				}
			} else if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) {
				if (gatt.getDevice().getBondState() != BluetoothDevice.BOND_NONE) {
					DebugLogger.w(TAG, ERROR_AUTH_ERROR_WHILE_BONDED);
					mCallbacks.onError(ERROR_AUTH_ERROR_WHILE_BONDED, status);
				}
			} else {
				mCallbacks.onError(ERROR_WRITE_DESCRIPTOR, status);
			}
		}

		@Override
		public void onCharacteristicChanged(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
			// Decode the new data
			int offset = 0;
			final int flags = characteristic.getValue()[offset]; // 1 byte
			offset += 1;

			final boolean wheelRevPresent = (flags & WHEEL_REVOLUTIONS_DATA_PRESENT) > 0;
			final boolean crankRevPreset = (flags & CRANK_REVOLUTION_DATA_PRESENT) > 0;

			int wheelRevolutions = 0;
			int lastWheelEventTime = 0;
			if (wheelRevPresent) {
				wheelRevolutions = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, offset);
				offset += 4;

				lastWheelEventTime = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset); // 1/1024 s
				offset += 2;

				// Notify listener about the new measurement
				mCallbacks.onWheelMeasurementReceived(wheelRevolutions, lastWheelEventTime);
			}

			int crankRevolutions = 0;
			int lastCrankEventTime = 0;
			if (crankRevPreset) {
				crankRevolutions = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
				offset += 2;

				lastCrankEventTime = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
				offset += 2;

				// Notify listener about the new measurement
				mCallbacks.onCrankMeasurementReceived(crankRevolutions, lastCrankEventTime);
			}
		}
	};

	private BroadcastReceiver mBondingBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			final int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
			final int previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);

			// skip other devices
			if (!device.getAddress().equals(mBluetoothGatt.getDevice().getAddress()))
				return;

			DebugLogger.i(TAG, "Bond state changed for: " + device.getName() + " new state: " + bondState + " previous: " + previousBondState);

			if (bondState == BluetoothDevice.BOND_BONDING) {
				mCallbacks.onBondingRequired();
				return;
			}
			if (bondState == BluetoothDevice.BOND_BONDED) {
				mCallbacks.onBonded();
			}
		}
	};

	public void readBatteryLevel() {
		readBatteryLevel(mBluetoothGatt);
	}

	/**
	 * Reading the current value of the battery level
	 */
	private void readBatteryLevel(final BluetoothGatt gatt) {
		if (mBatteryCharacteristic != null) {
			if ((mBatteryCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
				DebugLogger.d(TAG, "reading battery characteristic");
				gatt.readCharacteristic(mBatteryCharacteristic);
			}
		} else {
			DebugLogger.w(TAG, "Battery Level Characteristic is null");
		}
	}

	/**
	 * Enabling notification on Battery Level Characteristic
	 */
	private void enableBatteryLevelNotification(final BluetoothGatt gatt) {
		mBatteryLevelNotificationsEnabled = true;
		gatt.setCharacteristicNotification(mBatteryCharacteristic, true);
		final BluetoothGattDescriptor descriptor = mBatteryCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
		descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
		gatt.writeDescriptor(descriptor);
	}

	/**
	 * Enabling notification on CSC Measurement Characteristic
	 */
	private void enableCSCMeasurementNotification(final BluetoothGatt gatt) {
		gatt.setCharacteristicNotification(mCSCMeasurementCharacteristic, true);
		final BluetoothGattDescriptor descriptor = mCSCMeasurementCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
		descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
		gatt.writeDescriptor(descriptor);
	}

	@Override
	public void closeBluetoothGatt() {
		try {
			mContext.unregisterReceiver(mBondingBroadcastReceiver);
		} catch (Exception e) {
			// the receiver must have been not registered or unregistered before
		}
		if (mBluetoothGatt != null) {
			mBluetoothGatt.close();
			mBluetoothGatt = null;
		}
		mBatteryLevelNotificationsEnabled = false;
		mCallbacks = null;
		mLogSession = null;
	}

}