package com.megabit.musicall;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
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
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class BluetoothConnection {
    private BluetoothAdapter mBluetoothAdapter;

    private MainActivity mCurrActivity;

    private ArrayAdapter<String> mArrayAdapter = null;
    private ArrayList<BluetoothDevice> mDetectedDevices = null;
    private BluetoothDevice mSelectedDevice = null;

    private static final int REQUEST_ENABLE_BT = 43;
    private static final int DISCOVERABLE_DURATION = 60;
    public static final UUID uuid = UUID.fromString("d6a3cb53-b9ea-4ea3-b0fa-04d7999d2acf");

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

    private class BTAcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public BTAcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket,
            // because mmServerSocket is final
            BluetoothServerSocket tmp = null;
            try {
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("musicall", uuid);
            } catch (IOException e) { }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned
            while (true) {
                try {
                    Log.i(MainActivity.TAG, "server listening...");
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    break;
                }
                // If a connection was accepted
                if (socket != null) {
                    try {
                        mmServerSocket.close();
                    } catch (IOException e) {
                        break;
                    }
                    // Do work to manage the connection (in a separate thread)
                    manageServerSocket(socket);
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

        public BTConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;

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
                Log.i(MainActivity.TAG, "attempting to connect to server");
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                try {
                    mmSocket.close();
                } catch (IOException closeException) { }
                return;
            }

            // Do work to manage the connection (in a separate thread)
            manageClientSocket(mmSocket);
        }

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    public BluetoothConnection(MainActivity activity) {
        mCurrActivity = activity;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(activity, "bluetooth not supported", Toast.LENGTH_SHORT).show();
        } else if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mCurrActivity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    public void receiveDiscovery() {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_DURATION);
        mCurrActivity.startActivity(discoverableIntent);
        new Thread(new BTAcceptThread()).start();
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
        final ListView listview = (ListView) mCurrActivity.findViewById(R.id.listview);
        listview.setAdapter(mArrayAdapter);
        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> l, View v, int position, long id) {
                System.out.println(position);
                mSelectedDevice = mDetectedDevices.get(position);
                btConnect();
            }
        });
        // Register the BroadcastReceiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        mCurrActivity.registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy
        mBluetoothAdapter.startDiscovery();
    }

    public void btConnect() {
        new Thread(new BTConnectThread(mSelectedDevice)).start();
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
                } else break;
            }
            dis.close();
            socket.close();
        } catch (IOException e) {
            Log.e(MainActivity.TAG, "error: " + e.toString());
        }
    }

    private void manageServerSocket(BluetoothSocket socket) {
        Log.i(MainActivity.TAG, "got server socket");
        try {
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            int seek;
            while (true) {
                synchronized (mCurrActivity.seekQueue) {
                    mCurrActivity.seekQueue.wait();
                    seek = mCurrActivity.seekQueue.getAndSet(-1);
                }
                dos.writeByte(1);
                dos.writeInt(seek);
                dos.flush();
                Log.i(MainActivity.TAG, "wrote int " + seek);
            }
            //dos.close();
            //socket.close();
        } catch (IOException e) {
            Log.e(MainActivity.TAG, "error: " + e.toString());
        } catch (InterruptedException e) {
            // shouldn't happen
        }
    }
}
