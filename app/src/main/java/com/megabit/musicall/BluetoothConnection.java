package com.megabit.musicall;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.Set;

public class BluetoothConnection {
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;

    private boolean btScanning;
    private Handler btHandler;
    Activity mCurrActivity;

    ArrayAdapter<String> mArrayAdapter = null;

    private final static int REQUEST_ENABLE_BT = 43;
    private static final int BT_SCAN_PERIOD = 10000;
    private static final int DISCOVERABLE_DURATION = 300;

    public BluetoothConnection(Activity activity) {
        mCurrActivity = activity;
    }

    public void receiveDiscovery() {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_DURATION);
        mCurrActivity.startActivity(discoverableIntent);
    }

    public void discoverDevices() {
        mBluetoothManager = (BluetoothManager) mCurrActivity.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mCurrActivity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        mArrayAdapter = new ArrayAdapter<String>(mCurrActivity, android.R.layout.simple_list_item_1);

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        // If there are paired devices
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                // Add the name and address to an array adapter to show in a ListView
                mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        }
        final ListView listview = (ListView) mCurrActivity.findViewById(R.id.listview);
        System.out.println(mArrayAdapter.getCount());
        listview.setAdapter(mArrayAdapter);
        mBluetoothAdapter.startDiscovery();

        // Register the BroadcastReceiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        mCurrActivity.registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy
    }

    // Create a BroadcastReceiver for ACTION_FOUND
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Add the name and address to an array adapter to show in a ListView
                mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        }
    };

    /**
     * Attempts to connect to other device within BT_SCAN_PERIOD
     * @param enable
     */
    /*
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            btHandler.postDelayed(new Runnable() {
                //todo: change run() later
                public void run() {
                    btScanning = false;
                    bluetoothAdapter.stopLeScan(mLeScanCallback);
                }
            }, BT_SCAN_PERIOD);

            btScanning = true;
            bluetoothAdapter.startLeScan();
        } else {
            btScanning = false;
            bluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }
    */
}
