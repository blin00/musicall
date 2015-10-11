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
import android.net.Uri;
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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class BluetoothConnection {

    protected BluetoothAdapter mBluetoothAdapter;
    protected MainActivity mCurrActivity;
    protected BlockingQueue<Object> messageQueue;    // URI => send file; Integer => seek (-1 is pause); nothing else allowed
    public static final int REQUEST_ENABLE_BT = 43;
    public static final int REQUEST_ENABLE_BT_DISCOVERABLE = 44;

    public static final List<UUID> uuids;

    static {
        // cap at 3 for now
        ArrayList<UUID> _uuids = new ArrayList<>();
        _uuids.add(UUID.fromString("d6a3cb53-b9ea-4ea3-b0fa-04d7999d2acf"));
        _uuids.add(UUID.fromString("d6a3cb54-b9ea-4ea3-b0fa-04d7999d2acf"));
        _uuids.add(UUID.fromString("d6a3cb55-b9ea-4ea3-b0fa-04d7999d2acf"));
        //_uuids.add(UUID.fromString("d6a3cb56-b9ea-4ea3-b0fa-04d7999d2acf"));
        //_uuids.add(UUID.fromString("d6a3cb57-b9ea-4ea3-b0fa-04d7999d2acf"));
        uuids = Collections.unmodifiableList(_uuids);
    }

    public BluetoothConnection(MainActivity activity) {
        mCurrActivity = activity;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        messageQueue = new LinkedBlockingQueue<>();
        if (mBluetoothAdapter == null) {
            Toast.makeText(activity, "bluetooth not supported", Toast.LENGTH_SHORT).show();
            activity.finish();
        } else if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mCurrActivity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    /*


    // server
    private List<BluetoothSocket> mSockets;
    private static final int DISCOVERABLE_DURATION = 60;
    private volatile boolean serverDiscovering = false;
    private volatile BTAcceptThread mAcceptorThread = null;
    private List<BTServerThread> serverThreads;

    private class BTAcceptThread extends Thread {
        public BTAcceptThread() {
            serverDiscovering = true;
            mSockets = new ArrayList<>();
            serverThreads = new ArrayList<>();
        }

        @Override
        public void run() {
            int uuidIndex = 0;
            mSockets.clear();
            while (serverDiscovering && uuidIndex < uuids.size()) {
                BluetoothServerSocket serverSocket;
                BluetoothSocket socket;
                try {
                    serverSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("musicall", uuids.get(uuidIndex));
                    Log.i(MainActivity.TAG, "server listening to uuid: " + uuids.get(uuidIndex).toString());
                    socket = serverSocket.accept();
                    if (socket != null) {
                        mSockets.add(socket);
                        serverSocket.close();
                    }
                } catch (IOException e) {}

                uuidIndex++;
                try {
                    sleep(100);
                } catch (InterruptedException e) {}
            }
            manageServerSockets();
            mAcceptorThread = null;
        }

        /** Will cancel the listening socket, and cause the thread to finish */
        public void finishAddingConnections() {
            serverDiscovering = false;
        }
    }

    private class BTServerThread extends Thread {
        private BluetoothSocket socket;
        private BlockingQueue<Object> queue;

        public BTServerThread(BluetoothSocket socket) {
            this.socket = socket;
            queue = new LinkedBlockingQueue<>();
        }

        @Override
        public void run() {
            DataOutputStream dos;
            try {
                dos = new DataOutputStream(socket.getOutputStream());
            } catch (IOException e) {
                Log.e(MainActivity.TAG, "error: " + e.toString());
                return;
            }
            while (true) {
                Object msg = null;
                try {
                    msg = queue.take();
                } catch (InterruptedException e) {}
                if (msg == null) continue;
                Log.i(MainActivity.TAG, "got message on serverThread: " + msg.toString());
                if (msg instanceof Uri) {
                    Uri file = (Uri) msg;
                } else if (msg instanceof Integer) {
                    int seek = (int) msg;
                }
            }
        }

        public void enqueue(Object msg) {
            queue.add(msg);
        }
    }

    public void receiveDiscovery() {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_DURATION);
        mCurrActivity.startActivityForResult(discoverableIntent, REQUEST_ENABLE_BT_DISCOVERABLE);
        if (mAcceptorThread == null) {
            mAcceptorThread = new BTAcceptThread();
            mAcceptorThread.start();
        } else {
            Toast.makeText(mCurrActivity, "already accepting connections", Toast.LENGTH_SHORT).show();
        }
    }

    public void terminateSenderDiscovery() {
        mAcceptorThread.finishAddingConnections();
    }

    private void manageServerSockets() {
        new Thread() {
            @Override
            public void run() {
                Log.i(MainActivity.TAG, "got server sockets");
                serverThreads.clear();
                for (BluetoothSocket socket : mSockets) {
                    BTServerThread t = new BTServerThread(socket);
                    t.start();
                    serverThreads.add(t);
                }
                while (true) {
                    Object msg = null;
                    try {
                        msg = messageQueue.take();
                    } catch (InterruptedException e) {}
                    if (msg != null) {
                        for (BTServerThread t : serverThreads) {
                            t.enqueue(msg);
                        }
                    }
                }
            }
        }.start();
    }

    public void enqueue(Object msg) {
        messageQueue.add(msg);
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
        @Override
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
        final ListView listview = (ListView) mCurrActivity.findViewById(R.id.listview);
        listview.setAdapter(mArrayAdapter);
        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> l, View v, int position, long id) {
                System.out.println(position);
                mSelectedDevice = mDetectedDevices.get(position);
                btConnect();
            }
        });
        registerBroadcastReceiver();

        mBluetoothAdapter.startDiscovery();
        clientDiscovering = true;
    }

    public void btConnect() {
        new Thread(new BTConnectThread()).start();
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

    */
}
