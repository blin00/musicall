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

    protected BluetoothAdapter mBluetoothAdapter;
    protected MainActivity mCurrActivity;
    protected static final int REQUEST_ENABLE_BT = 43;
    protected static final ArrayList<UUID> uuids = new ArrayList<>();

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

}
