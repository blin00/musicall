package com.megabit.musicall;

import android.app.AlertDialog;
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
    private static final int REQUEST_ENABLE_BT = 43;
    public static final ArrayList<UUID> uuids = new ArrayList<>();

    public BluetoothConnection(MainActivity activity) {
        mCurrActivity = activity;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(activity, "bluetooth not supported", Toast.LENGTH_SHORT).show();
        } else if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mCurrActivity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        uuids.add(UUID.fromString("d6a3cb53-b9ea-4ea3-b0fa-04d7999d2acf"));
        uuids.add(UUID.fromString("d6a3cb54-b9ea-4ea3-b0fa-04d7999d2acf"));
        uuids.add(UUID.fromString("d6a3cb55-b9ea-4ea3-b0fa-04d7999d2acf"));
        uuids.add(UUID.fromString("d6a3cb56-b9ea-4ea3-b0fa-04d7999d2acf"));
        uuids.add(UUID.fromString("d6a3cb57-b9ea-4ea3-b0fa-04d7999d2acf"));
    }



    // server
    private ArrayList<BluetoothSocket> mSockets = null;
    private static final int DISCOVERABLE_DURATION = 60;
    private boolean serverDiscovering;
    private BTAcceptThread mAcceptorThread;

    private class BTAcceptThread extends Thread {

        public BTAcceptThread() {
            serverDiscovering = true;
        }

        public void run() {
            int uuidIndex = 0;
            while (uuidIndex < uuids.size() && serverDiscovering) {
                BluetoothServerSocket tmpServerSocket = null;
                try {
                    tmpServerSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("musicall", uuids.get(uuidIndex));
                } catch (IOException e) { }

                BluetoothSocket socket = null;
                // Keep listening until exception occurs or a socket is returned
                while (true) {
                    try {
                        Log.i(MainActivity.TAG, "server listening to uuid: " + uuids.get(uuidIndex).toString());
                        socket = tmpServerSocket.accept();
                    } catch (IOException e) {
                        break;
                    }
                    // If a connection was accepted
                    if (socket != null) {
                        try {
                            tmpServerSocket.close();
                        } catch (IOException e) {
                            break;
                        }
                        // Do work to manage the connection (in a separate thread)
                        mSockets.add(socket);
                        break;
                    }
                }
                uuidIndex++;
            }
            manageServerSocket(mSockets);
        }

        /** Will cancel the listening socket, and cause the thread to finish */
        public void finishAddingConnections() {
            serverDiscovering = false;
        }
    }

    public void receiveDiscovery() {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_DURATION);
        mCurrActivity.startActivity(discoverableIntent);
        mAcceptorThread = new BTAcceptThread();
        new Thread(mAcceptorThread).start();
    }

    public void terminateSenderDiscovery() {
        mAcceptorThread.finishAddingConnections();
    }

    private void manageServerSocket(ArrayList<BluetoothSocket> sockets) {
        Log.i(MainActivity.TAG, "got server sockets");
        for (BluetoothSocket socket : sockets) {
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






    // client
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

    public void registerBroadcastReceiver() {
        // Register the BroadcastReceiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        mCurrActivity.registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy
    }

    public void unregisterBroadcastReceiver() {
        mCurrActivity.unregisterReceiver(mReceiver);
    }

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
                if(dialog.isShowing()) {
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
                } else break;
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

}
