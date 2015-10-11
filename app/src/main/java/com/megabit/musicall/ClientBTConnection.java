package com.megabit.musicall;

import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

public class ClientBTConnection extends BluetoothConnection{
    public ClientBTConnection(MainActivity activity) {
        super(activity);
    }

    private ArrayAdapter<String> mArrayAdapter = null;
    private ArrayList<BluetoothDevice> mDetectedDevices = null;
    private BluetoothDevice mSelectedDevice = null;
    private BluetoothSocket mSocket;
    private boolean clientDiscovering;

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

    private class BTConnectThread extends Thread {

        public void run() {
            // Cancel discovery because it will slow down the connection
            mBluetoothAdapter.cancelDiscovery();
            mSocket = null;
            int uuidIndex = 0;
            while (mSocket == null && clientDiscovering) {
                // Get a BluetoothSocket to connect with the given BluetoothDevice
                try {
                    mSocket = mSelectedDevice.createRfcommSocketToServiceRecord(uuids.get(uuidIndex));
                } catch (IOException e) { }

                try {
                    // Connect the device through the socket. This will block
                    // until it succeeds or throws an exception
                    Log.i(MainActivity.TAG, "attempting to connect to server with uuid: " + uuids.get(uuidIndex).toString());
                    mSocket.connect();
                } catch (IOException connectException) {
                    mSocket = null;
                    uuidIndex = (uuidIndex + 1) % uuids.size();
                }
            }

            // Do work to manage the connection (in a separate thread)
            if (mSocket != null) {
                manageClientSocket(mSocket);
            }
        }

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mSocket.close();
            } catch (IOException e) { }
        }
    }

    public void registerBroadcastReceiver() {
        // Register the BroadcastReceiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        mCurrActivity.registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy
    }

    public void unregisterBroadcastReceiver() {
        mCurrActivity.unregisterReceiver(mReceiver);
    }

    public void discoverDevices() {
        mArrayAdapter = new ArrayAdapter<>(mCurrActivity, android.R.layout.simple_list_item_1);
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
        final ListView listview = new ListView(mCurrActivity);
        listview.setAdapter(mArrayAdapter);
        AlertDialog.Builder builder = new AlertDialog.Builder(mCurrActivity);
        builder.setTitle("Devices");
        builder.setView(listview);
        final AlertDialog dialog = builder.create();
        dialog.show();

        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> l, View v, int position, long id) {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
            mSelectedDevice = mDetectedDevices.get(position);
            new Thread(new BTConnectThread()).start();
            }
        });
        registerBroadcastReceiver();

        mBluetoothAdapter.startDiscovery();
        clientDiscovering = true;
    }

    private void manageClientSocket(BluetoothSocket socket) {
        Log.i(MainActivity.TAG, "got client socket");
        try {
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            while (true) {
                byte cmd = dis.readByte();
                Log.i(MainActivity.TAG, "read cmd: " + cmd);
                if (cmd == 1) { // seek
                    mCurrActivity.seekTo(dis.readInt());
                } else if (cmd == 2) {
                    boolean playing = dis.readBoolean();
                    mCurrActivity.setPlaying(playing);
                } else if (cmd == 0) {
                    mCurrActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mCurrActivity, "loading file", Toast.LENGTH_SHORT).show();
                        }
                    });
                    int size = dis.readInt();
                    Log.i(MainActivity.TAG, "file size: " + size);
                    byte[] buf = new byte[size];
                    int offset = 0;
                    while (size > 0) {
                        int read = dis.read(buf, offset, size);
                        if (read == -1) throw new EOFException("file smaller than expected");
                        offset += read;
                        size -= read;
                    }
                    Log.i(MainActivity.TAG, "done reading file");
                    mCurrActivity.setSourceToArray(buf);
                } else {
                    break;
                }
            }
            dis.close();
            socket.close();
        } catch (IOException e) {
            Log.e(MainActivity.TAG, "error: " + e.toString());
        }
    }

    public void terminateReceiverDiscovery() {
        clientDiscovering = false;
    }

    public void endReceiver() {
        clientDiscovering = false;
        mArrayAdapter = null;
        mDetectedDevices = null;
        mSelectedDevice = null;
        if (mSocket != null) {
            try {
                mSocket.close();
            } catch (IOException ioe) {

            }
        }
        mSocket = null;
    }

}
