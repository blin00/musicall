package com.megabit.musicall;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;

import android.app.Activity;
import android.bluetooth.BluetoothManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;

import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import android.widget.Toast;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    BluetoothConnection btConn = null;
    private boolean btScanning;
    private Handler btHandler;
    private MediaPlayer mediaPlayer;

    private static final long BT_SCAN_PERIOD = 10000;
    private static final int READ_REQUEST_CODE = 42;
    private static final int REQUEST_ENABLE_BT = 43;
    private static final String TAG = "MCAL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        mediaPlayer = new MediaPlayer();

        btConn = new BluetoothConnection(this);
        btConn.discoverDevices();
//        receiveDiscovery();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mediaPlayer.release();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            mediaPlayer.stop();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {
        // The ACTION_OPEN_DOCUMENT intent was sent with the request code
        // READ_REQUEST_CODE. If the request code seen here doesn't match, it's the
        // response to some other intent, and the code below shouldn't run at all.

        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // The document selected by the user won't be returned in the intent.
            // Instead, a URI to that document will be contained in the return intent
            // provided to this method as a parameter.
            // Pull that URI using resultData.getData().
            Uri uri;
            if (resultData != null) {
                uri = resultData.getData();
                Log.i(TAG, "Uri: " + uri.toString());
                mediaPlayer.reset();
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mp, int what, int extra) {
                        Toast.makeText(getApplicationContext(), "invalid music file (2)", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "some error occurred (" + what + ", " + extra + ")");
                        return true;
                    }
                });
                mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        Toast.makeText(getApplicationContext(), "music started", Toast.LENGTH_SHORT).show();
                        mp.start();
                    }
                });
                try {
                    mediaPlayer.setDataSource(getApplicationContext(), uri);
                } catch (IOException e) {
                    Toast.makeText(getApplicationContext(), "invalid music file (1)", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "invalid data source");
                    return;
                }
                mediaPlayer.prepareAsync();
            }
        }
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

    /**
     * Fires an intent to spin up the "file chooser" UI and select an image.
     */
    public void performFileSearch() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);

        // Filter to only show results that can be "opened", such as a
        // file (as opposed to a list of contacts or timezones)
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // Filter to show everything
        intent.setType("*/*");

        startActivityForResult(intent, READ_REQUEST_CODE);
    }
}
