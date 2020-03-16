package br.com.training.ble_tests;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static int REQUEST_ENABLE_BT = 4;
    private static int STATE_CONNECTED = 1;

    private static String HT_SENSOR_ADDRESS = "00001809-0000-1000-8000-00805f9b34fb";

    private UUID HEART_THERMOMETER_SERVICE_UUID = convertFromInteger(0x180D);
    private UUID HEART_THERMOMETER_MEASUREMENT_CHAR_UUID = convertFromInteger(0x2A37);
    private UUID HEART_THERMOMETER_CONTROL_POINT_CHAR_UUID = convertFromInteger(0x2A39);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BluetoothAdapter bluetoothAdapter;

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT);
        }

        BluetoothAdapter.LeScanCallback scanCallback = new BluetoothAdapter.LeScanCallback() {
            @Override
            public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {

            }
        };

        bluetoothAdapter.startLeScan(scanCallback);
    }

    public UUID convertFromInteger(int i) {
        final long MSB = 0x0000000000001000L;
        final long LSB = 0x800000805f9b34fbL;

        long value = i & 0xFFFFFFFF;

        return new UUID(MSB | (value << 32), LSB);
    }
}
