package com.megabit.musicall;

import android.app.Activity;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private MediaPlayer mediaPlayer;
    BluetoothConnection btConn;

    private static final int READ_REQUEST_CODE = 42;
    private static final int BT_DISCOVERABILITY_REQUEST_CODE = 41;
    private static final String TAG = "MCAL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                performFileSearch();
            }
        });

        mediaPlayer = new MediaPlayer();

        btConn = new BluetoothConnection(this);

        Button receiverButton = (Button) findViewById(R.id.receiverButton);
        Button senderButton = (Button) findViewById(R.id.senderButton);
        receiverButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btConn.discoverDevices();
            }
        });
        senderButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btConn.receiveDiscovery();
            }
        });
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
        } else if (requestCode == BT_DISCOVERABILITY_REQUEST_CODE && resultCode == Activity.RESULT_CANCELED) {
            //TODO?
        }
    }



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

    public void outFile(Uri uri, BluetoothSocket socket) throws IOException {
        BufferedInputStream bs = new BufferedInputStream(getContentResolver().openInputStream(uri));
        OutputStream os = socket.getOutputStream();
        try {
            int bufferSize = 8192;
            byte[] buffer = new byte[bufferSize];

            int len = 0;
            while ((len = bs.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
        } finally {
            bs.close();
            os.flush();
            os.close();
            bs.close();
        }
    }
}
