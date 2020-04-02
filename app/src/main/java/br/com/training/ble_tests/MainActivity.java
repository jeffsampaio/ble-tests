package br.com.training.ble_tests;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    @BindView(R.id.txt_device)
    TextView txtDevice;

    @BindView(R.id.button_scan_devices)
    Button buttonScanDevices;

    @BindView(R.id.txt_temperature)
    TextView txtTemperature;

    private static String TAG = "MainActivity";

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_ENABLE_LOCATION = 2;
    private static final long SCAN_PERIOD = 15000;  // Stops scanning after 15 seconds.

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private BluetoothDevice mDevice;

    private String mDeviceAddress;
    private boolean mScanning;
    private Handler mHandler;

    private BluetoothLeService mBluetoothLeService;
    private boolean gattServiceDiscovered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        mDeviceAddress = "1C:87:74:01:73:10";
        mHandler = new Handler();

        buttonScanDevices.setOnClickListener(this);

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        initComponents();
    }

    /**
     * Initialize components.
     */
    private void initComponents() {
        checkBleAvailability();
        initBluetooth();
    }

    /**
     * Check whether BLE is supported on the device.
     */
    private void checkBleAvailability() {
        if (!(getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /**
     * Request Bluetooth permission.
     */
    private void requestBluetoothEnable() {
        startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT);
    }

    /**
     * Initialize Bluetooth.
     */
    private void initBluetooth() {
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            requestBluetoothEnable();
        }

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

        if (mBluetoothLeService != null) {
            if (!mBluetoothLeService.connect(mDeviceAddress)) {
                mBluetoothLeService.disconnect();
                mBluetoothLeService.close();
            }
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);

            Log.d(TAG, "Connect request result = " + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        mDevice = null;

        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unbindService(mServiceConnection);

        mDevice = null;

        mBluetoothLeService = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }

        initBluetooth();

        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * When device founded from Scanner.
     *
     * @param device
     */
    public void onDeviceFounded(BluetoothDevice device) {
        if (device != null) {
            mDevice = device;

            txtDevice.setText(getString(R.string.txt_device) + " " + mDevice.getName() + "\n"
                    + getString(R.string.txt_device_address) + " " + mDevice.getAddress());

            unpairDevice(mDevice);

            mDevice.createBond();
        }
    }

    /**
     * Unpair device from cellphone.
     *
     * @param device
     * @return
     */
    private boolean unpairDevice(BluetoothDevice device) {
        boolean confirmed = false;
        if (!device.getAddress().isEmpty()) {
            BluetoothDevice mBluetoothDevice = BluetoothAdapter.getDefaultAdapter().
                    getRemoteDevice(device.getAddress());
            try {
                Method m = mBluetoothDevice.getClass()
                        .getMethod("removeBond", (Class[]) null);
                m.invoke(mBluetoothDevice, (Object[]) null);
                confirmed = true;
            } catch (Exception e) {
                Log.d(TAG, "error removing pairing " + e.getMessage());
            }
        }
        return confirmed;
    }

    /**
     * Search for BLE devices.
     */
    private void scanLeDevice() {
        // Stops scanning after a pre-defined scan period.
        mHandler.postDelayed(() -> {
            mScanning = false;
            if (mBluetoothAdapter.isEnabled()) mBluetoothLeScanner.stopScan(mLeScanCallback);

            Toast.makeText(this, R.string.stop_scanning_devices, Toast.LENGTH_SHORT).show();
        }, SCAN_PERIOD);

        mScanning = true;
        mBluetoothLeScanner.startScan(mLeScanCallback);

        Toast.makeText(this, R.string.scanning_devices, Toast.LENGTH_SHORT).show();
    }

    /**
     * Defines ScanCallback.
     */
    private final ScanCallback mLeScanCallback = new android.bluetooth.le.ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice btDevice = result.getDevice();

            if (btDevice == null || btDevice.getName() == null) {
                return;
            }

            mBluetoothLeScanner.stopScan(mLeScanCallback);

            onDeviceFounded(btDevice);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "onScanFailed() " + errorCode);
        }
    };

    /**
     * Make update of gatt intent filter.
     *
     * @return IntentFilter
     */
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);

        return intentFilter;
    }

    /**
     * Defines service connection.
     */
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) iBinder).getService();

            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }

            // Automatically connects to the device upon successful start-up initialization.
            tryingConnect();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                Log.i(TAG, "ACTION_GATT_CONNECTED");
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.i(TAG, "ACTION_GATT_DISCONNECTED");

                tryingConnect();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Log.w(TAG, "ACTION_GATT_SERVICES_DISCOVERED");
                gattServiceDiscovered = true;
                listenTemperature();
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                String jsonData = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);

                txtTemperature.setText(jsonData);
            }
        }
    };

    /**
     * Temperature Characteristic indication.
     */
    public void listenTemperature() {
        BluetoothGatt mBluetoothGatt = mBluetoothLeService.getBluetoothGatt();

        if (mBluetoothGatt == null) return;

        BluetoothGattCharacteristic bchar = mBluetoothGatt.getService(UUID.fromString(GattAttributes.SERVICE_HEALTH_THERMOMETER))
                .getCharacteristic(UUID.fromString(GattAttributes.CHARACTERISTIC_TEMPERATURE_MEASUREMENT));

        mBluetoothLeService.setCharacteristicNotification(bchar, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE, true);
    }

    /**
     * Connect to device.
     *
     * @return boolean
     */
    private boolean tryingConnect() {
        Log.i(TAG, "tryingConnect()");
        if (mBluetoothLeService != null) {
            return mBluetoothLeService.connect(mDeviceAddress);
        }

        return false;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.button_scan_devices:
                if (mBluetoothAdapter.isEnabled() && mBluetoothLeScanner != null) {
                    if (!mScanning) scanLeDevice();
                } else {
                    Toast.makeText(this, R.string.disabled_bluetooth, Toast.LENGTH_LONG).show();
                }

                break;
        }
    }
}
