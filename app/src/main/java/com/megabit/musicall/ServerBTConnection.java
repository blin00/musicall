package com.megabit.musicall;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ServerBTConnection extends BluetoothConnection {
    // server
    private ArrayList<BluetoothSocket> mSockets = null;
    private static final int DISCOVERABLE_DURATION = 60;
    private volatile boolean serverDiscovering;
    private BTAcceptThread mAcceptorThread;
    private BlockingQueue<Object> messageQueue;
    private ListView receiverList = null;
    private static final int maxReceiverListHeight = 200;
    private ArrayAdapter<String> mArrayAdapter = null;
    private int currentReceiverListHeight = 0;


    public ServerBTConnection(MainActivity activity) {
        super(activity);
        messageQueue = new LinkedBlockingQueue<>();
        receiverList = (ListView) activity.findViewById(R.id.receiverList);
        mArrayAdapter = new ArrayAdapter<>(mCurrActivity, android.R.layout.simple_list_item_1);
        receiverList.setAdapter(mArrayAdapter);
    }

    public void enqueue(Object msg) {
        messageQueue.add(msg);
    }

    private class BTAcceptThread extends Thread {
        private volatile BluetoothServerSocket tmpServerSocket;
        public BTAcceptThread() {
            serverDiscovering = true;
        }

        public void run() {
            int uuidIndex = 0;
            // Keep listening until exception occurs or a socket is returned
            while (serverDiscovering && uuidIndex < uuids.size()) {
                BluetoothSocket socket;
                try {
                    tmpServerSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("musicall", uuids.get(uuidIndex));
                    Log.i(MainActivity.TAG, "server listening to uuid: " + uuids.get(uuidIndex).toString());
                    socket = tmpServerSocket.accept();
                    if (socket != null) {
                        mSockets.add(socket);
                        tmpServerSocket.close();
                        currentReceiverListHeight = Math.min(currentReceiverListHeight + 40, maxReceiverListHeight);
                        final BluetoothDevice device = socket.getRemoteDevice();
                        final int finalUuidIndex = uuidIndex;
                        mCurrActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mArrayAdapter.add(Integer.toString(finalUuidIndex + 1) + ") " + device.getName() + " " + device.getAddress());
                                mArrayAdapter.notifyDataSetChanged();
                            }
                        });

                    }
                } catch (IOException e) {}

                uuidIndex++;
                try {
                    sleep(100);
                } catch (InterruptedException e) {}
            }
            manageServerSocket(mSockets);
        }

        /** Will cancel the listening socket, and cause the thread to finish */
        public void finishAddingConnections() {
            serverDiscovering = false;
            if (tmpServerSocket != null) {
                try {
                    tmpServerSocket.close();
                } catch (IOException e) {}
            }
        }
    }

    public void receiveDiscovery() {
        mSockets = new ArrayList<>();
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_DURATION);
        mCurrActivity.startActivity(discoverableIntent);
        mAcceptorThread = new BTAcceptThread();
        mAcceptorThread.start();
    }

    public void terminateSenderDiscovery() {
        mAcceptorThread.finishAddingConnections();
    }

    private void manageServerSocket(ArrayList<BluetoothSocket> sockets) {
        Log.i(MainActivity.TAG, "got server sockets");
        messageQueue.clear();
        ArrayList<DataOutputStream> streams = new ArrayList<>();
        for (BluetoothSocket s : sockets) {
            try {
                streams.add(new DataOutputStream(s.getOutputStream()));
            } catch (IOException e) {}
        }
        while (true) {
            Object msg = null;
            try {
                msg = messageQueue.take();
            } catch (InterruptedException e) {}
            if (msg == null) continue;
            Log.i(MainActivity.TAG, "sending: " + msg.toString());
            int delay = 20;
            for (DataOutputStream dos : streams) {
                try {
                    if (msg instanceof Integer) {
                        int seek = (int) msg;
                        seek -= delay;
                        if (seek < 0) seek = 0;
                        dos.writeByte(1);
                        dos.writeInt(seek);
                        dos.flush();
                        delay += 20;
                    } else if (msg instanceof Boolean) {
                        boolean play = (boolean) msg;
                        dos.writeByte(2);
                        dos.writeBoolean(play);
                        dos.flush();
                    } else if (msg instanceof Uri) {
                        Uri file = (Uri) msg;
                        dos.writeByte(0);
                        mCurrActivity.sendFile(file, dos);
                    }
                } catch (IOException e) {
                    Log.e(MainActivity.TAG, "error: " + e.toString());
                }
            }
        }
    }

    private void setReceiverListHeight(int height) {
        ViewGroup.LayoutParams params = receiverList.getLayoutParams();
        params.height = height;
        receiverList.setLayoutParams(params);
        receiverList.requestLayout();
    }

    public void endSender() {
        serverDiscovering = false;
        if (mSockets != null) {
            for (BluetoothSocket socket : mSockets) {
                try {
                    socket.close();
                } catch (IOException ioe) {

                }
            }
            mSockets = null;
        }
        currentReceiverListHeight = 0;
        setReceiverListHeight(currentReceiverListHeight);
        mArrayAdapter.clear();
        mArrayAdapter.notifyDataSetChanged();
    }
}
