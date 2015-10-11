package com.megabit.musicall;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {
    private MediaPlayer mediaPlayer;
    private BluetoothConnection btConn;
    private TextView currentSong;
    private SeekBar seekBar;
    private Handler updateSeekHandler;
    private Runnable updateSeekTask;
    public final AtomicInteger seekQueue = new AtomicInteger(-1);

    private static final int READ_REQUEST_CODE = 42;
    private static final int BT_DISCOVERABILITY_REQUEST_CODE = 41;
    public static final String TAG = "Musicall";

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
        currentSong = (TextView) findViewById(R.id.currentSong);
        seekBar = (SeekBar) findViewById(R.id.musicSeekBar);
        resetPlayer();
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                updateSeekHandler.removeCallbacks(updateSeekTask);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                Log.i(TAG, "seek to: " + progress);
                synchronized(seekQueue) {
                    seekQueue.set(progress);
                    seekQueue.notifyAll();
                }
                if (mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                    // in case playback stopped because reached end
                    mediaPlayer.start();
                }
                updateSeekHandler.post(updateSeekTask);
            }
        });
        updateSeekHandler = new Handler();
        updateSeekTask = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null) {
                    seekBar.setProgress(mediaPlayer.getCurrentPosition());
                }
                updateSeekHandler.postDelayed(this, 250);
            }
        };
        updateSeekHandler.post(updateSeekTask);
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

        if (id == R.id.action_settings) {
            mediaPlayer.stop();
            resetPlayer();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void resetPlayer() {
        currentSong.setText("<none>");
        seekBar.setEnabled(false);
        mediaPlayer.reset();
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
            final Uri uri;
            if (resultData != null) {
                uri = resultData.getData();
                Log.i(TAG, "Uri: " + uri.toString());
                resetPlayer();
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mp, int what, int extra) {
                        Log.e(TAG, "some error occurred (" + what + ", " + extra + ")");
                        return true;
                    }
                });
                mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        currentSong.setText(uri.getLastPathSegment());
                        seekBar.setMax(mediaPlayer.getDuration());
                        seekBar.setEnabled(true);
                        Toast.makeText(getApplicationContext(), "music started", Toast.LENGTH_SHORT).show();
                        mp.start();
                    }
                });
                try {
                    mediaPlayer.setDataSource(getApplicationContext(), uri);
                } catch (IOException e) {
                    Toast.makeText(getApplicationContext(), "invalid music file", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "invalid data source");
                    return;
                }
                mediaPlayer.prepareAsync();
            }
        } else if (requestCode == BT_DISCOVERABILITY_REQUEST_CODE && resultCode == Activity.RESULT_CANCELED) {
            // TODO: ?
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

    public void seekTo(int loc) {
        mediaPlayer.seekTo(loc);
    }
}
