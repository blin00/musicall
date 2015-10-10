package com.megabit.musicall;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class BluetoothConnection {
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;

    private boolean btScanning;
    private Handler btHandler;
    Activity mCurrActivity;

    ArrayAdapter<String> mArrayAdapter = null;
    ArrayList<BluetoothDevice> mDetectedDevices = null;
    BluetoothDevice mSelectedDevice = null;

    private final static int REQUEST_ENABLE_BT = 43;
    private static final int BT_SCAN_PERIOD = 10000;
    private static final int DISCOVERABLE_DURATION = 300;
    UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private class BTAcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public BTAcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket,
            // because mmServerSocket is final
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("BT_SERVER", uuid);
            } catch (IOException e) { }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned
            while (true) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    break;
                }
                // If a connection was accepted
                if (socket != null) {
                    // Do work to manage the connection (in a separate thread)
                    manageConnectedSocket(socket);
                    try {
                        mmServerSocket.close();
                    } catch (IOException e) {
                        break;
                    }
                    break;
                }
            }
        }

        /** Will cancel the listening socket, and cause the thread to finish */
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) { }
        }
    }

    private class BTConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public BTConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                tmp = device.createRfcommSocketToServiceRecord(uuid);
            } catch (IOException e) { }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            mBluetoothAdapter.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                try {
                    mmSocket.close();
                } catch (IOException closeException) { }
                return;
            }

            // Do work to manage the connection (in a separate thread)
            manageConnectedSocket(mmSocket);
        }

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    public BluetoothConnection(Activity activity) {
        mCurrActivity = activity;
        mBluetoothManager = (BluetoothManager) mCurrActivity.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mCurrActivity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    public void receiveDiscovery() {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_DURATION);
        mCurrActivity.startActivity(discoverableIntent);
        BTAcceptThread acceptorThread = new BTAcceptThread();
        acceptorThread.run();
    }

    public void discoverDevices() {
        mArrayAdapter = new ArrayAdapter<String>(mCurrActivity, android.R.layout.simple_list_item_1);
        mDetectedDevices = new ArrayList<>();
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        // If there are paired devices
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                // Add the name and address to an array adapter to show in a ListView
                mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                mDetectedDevices.add(device);
            }
        }
        final ListView listview = (ListView) mCurrActivity.findViewById(R.id.listview);
        listview.setAdapter(mArrayAdapter);
        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> l, View v, int position, long id) {
                System.out.println(position);
                mSelectedDevice = mDetectedDevices.get(position);
                BTConnect();
            }
        });
        mBluetoothAdapter.startDiscovery();

        // Register the BroadcastReceiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        mCurrActivity.registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy
    }

    public void BTConnect() {
        BTConnectThread btThread = new BTConnectThread(mSelectedDevice);
        btThread.run();
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
                mDetectedDevices.add(device);
            }
        }
    };

    private void manageConnectedSocket(BluetoothSocket socket) {
        //TODO
        //close socket when done
    }

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
