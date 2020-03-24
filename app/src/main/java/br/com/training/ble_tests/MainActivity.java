package br.com.training.ble_tests;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
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

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    @BindView(R.id.txt_device)
    TextView txtDevice;

    @BindView(R.id.button_scan_devices)
    Button buttonScanDevices;

    @BindView(R.id.txt_temperature)
    TextView txtTemperature;

    @BindView(R.id.button_read_temp)
    Button buttonReadTemperature;

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
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        mDeviceAddress = "1C:87:74:01:73:10";
        mHandler = new Handler();

        buttonScanDevices.setOnClickListener(this);
        buttonReadTemperature.setOnClickListener(this);

        initComponents();
    }

    /**
     * Initialize components
     */
    private void initComponents() {
        checkBleAvailability();
        checkPermissions();
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
     * Initialize Bluetooth.
     */
    private void initBluetooth() {
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
    }

    /**
     * Check if Bluetooth is supported on the device.
     */
    private void checkBluetoothAvailability() {
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
    }

    /**
     * Checks if you have permission to use.
     * Required bluetooth ble and location.
     */
    public void checkPermissions() {
        if (BluetoothAdapter.getDefaultAdapter() != null && !BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            requestBluetoothEnable();
        } else if (!hasLocationPermissions()) {
            requestLocationPermission();
        }
    }

    /**
     * Request Bluetooth permission
     */
    private void requestBluetoothEnable() {
        startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT);
    }

    /**
     * Checks whether the location permission was given.
     *
     * @return boolean
     */
    public boolean hasLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /**
     * Request Location permission.
     */
    protected void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_ENABLE_LOCATION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // If request is cancelled, the result arrays are empty.
        if((requestCode == REQUEST_ENABLE_LOCATION) &&
            (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            requestLocationPermission();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Ensures Bluetooth is enabled on the device. If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        // User choose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK) {
            requestBluetoothEnable();
        } else {
            requestLocationPermission();
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mBluetoothLeScanner != null) {
            scanLeDevice(false);
        }

        mDevice = null;
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
     * Search for BLE devices
     * @param enable
     */
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(() -> {
                mScanning = false;
                mBluetoothLeScanner.stopScan(mLeScanCallback);

                Toast.makeText(this, R.string.stop_scanning_devices, Toast.LENGTH_SHORT).show();
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothLeScanner.startScan(mLeScanCallback);

            Toast.makeText(this, R.string.scanning_devices, Toast.LENGTH_SHORT).show();
        } else {
            mScanning = false;
            mBluetoothLeScanner.stopScan(mLeScanCallback);
        }
    }

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

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.button_scan_devices:
                if (mBluetoothLeScanner != null && !mScanning) {
                    scanLeDevice(true);
                }

                break;
            case R.id.button_read_temp:
                if (mDevice != null) {
                    txtTemperature.setText(mDevice.getAddress());
                }

                break;
        }
    }
}
