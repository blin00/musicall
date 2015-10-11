package com.megabit.musicall;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class ServerBTConnection extends BluetoothConnection {
    public ServerBTConnection(MainActivity activity) {
        super(activity);
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

                BluetoothSocket socket;
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
                        BluetoothDevice remote = socket.getRemoteDevice();
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
        mSockets = new ArrayList<>();
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
    }
}
