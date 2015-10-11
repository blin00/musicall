package com.megabit.musicall;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.res.ResourcesCompat;
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

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {
    private MediaPlayer mediaPlayer;
    private ClientBTConnection clientBTConn;
    private ServerBTConnection serverBTConn;
    private TextView currentSong;
    private SeekBar seekBar;
    private Handler updateSeekHandler;
    private Runnable updateSeekTask;
    private int pauseImg;
    private int playImg;
    private FloatingActionButton playPauseButton;
    private FloatingActionButton stopButton;

    public static final int READ_REQUEST_CODE = 42;
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

        clientBTConn = new ClientBTConnection(this);
        serverBTConn = new ServerBTConnection(this);

        final Button receiverButton = (Button) findViewById(R.id.receiverButton);
        final Button senderButton = (Button) findViewById(R.id.senderButton);

        playPauseButton = (FloatingActionButton) findViewById(R.id.playPause);

        stopButton = (FloatingActionButton) findViewById(R.id.stop);

        receiverButton.setOnClickListener(new View.OnClickListener() {
            private boolean started = false;

            @Override
            public void onClick(View v) {
                if (!started) {
                    started = true;
                    serverBTConn.endSender();
                    clientBTConn.discoverDevices();
                } else {
                    started = false;
                    clientBTConn.terminateReceiverDiscovery();
                }
            }
        });
        senderButton.setOnClickListener(new View.OnClickListener() {
            private boolean started = false;

            @Override
            public void onClick(View v) {
                if (!started) {
                    started = true;
                    clientBTConn.endReceiver();
                    serverBTConn.receiveDiscovery();
                    senderButton.setText("Done");
                } else {
                    started = false;
                    serverBTConn.terminateSenderDiscovery();
                    senderButton.setText("Add Receivers");
                }
            }
        });
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mediaPlayer.stop();
                resetPlayer();
            }
        });
        playPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean currPlaying = mediaPlayer.isPlaying();
                serverBTConn.enqueue(!currPlaying);
                setPlaying(!currPlaying);
            }
        });

        pauseImg = getResources().getIdentifier("@drawable/pause", null, getPackageName());
        playImg = getResources().getIdentifier("@drawable/play", null, getPackageName());

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
                serverBTConn.enqueue(progress);
                if (mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                }
                updateSeekHandler.post(updateSeekTask);
            }
        });
        updateSeekHandler = new Handler();
        updateSeekTask = new Runnable() {
            @Override
            public void run() {
            if (mediaPlayer != null) {
                try {
                    seekBar.setProgress(mediaPlayer.getCurrentPosition());
                } catch (IllegalStateException e) {
                    Log.e(TAG, "warning: caught MediaPlayer ISE");
                }
            }
            updateSeekHandler.postDelayed(this, 250);
            }
        };
        updateSeekHandler.post(updateSeekTask);
        //clientBTConn.registerBroadcastReceiver();
    }

    public void setPlaying(final boolean playing) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (playing) {
                    Log.i(TAG, "playing");
                    mediaPlayer.start();
                    playPauseButton.setImageDrawable(ResourcesCompat.getDrawable(getResources(), pauseImg, null));
                } else {
                    Log.i(TAG, "pausing");
                    mediaPlayer.pause();
                    playPauseButton.setImageDrawable(ResourcesCompat.getDrawable(getResources(), playImg, null));
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        clientBTConn.unregisterBroadcastReceiver();
    }

    @Override
    protected void onResume() {
        super.onResume();
        clientBTConn.registerBroadcastReceiver();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mediaPlayer.release();
        clientBTConn.unregisterBroadcastReceiver();
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

        if (id == R.id.Credits) {

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);

            // set title
            alertDialogBuilder.setTitle("People who contributed to this app:");

            // set dialog message
            alertDialogBuilder
                    .setMessage("Created by: \nBrandon Lin, Zhongxia Yan \nUtsav Baral, " +
                            "Jonathan Ngan \nEric Zhang, and Michael Zhao\nfor Calhacks 2.0")
                    .setCancelable(true)
                    .setNegativeButton("Return", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // if this button is clicked, just close
                            // the dialog box and do nothing
                            dialog.cancel();
                        }
                    });

            // create alert dialog
            AlertDialog alertDialog = alertDialogBuilder.create();

            // show it
            alertDialog.show();
        }
        if (id == R.id.About) {

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
            alertDialogBuilder.setTitle("About this App");
            alertDialogBuilder
                    .setMessage("Syncs music across multiple phones(connected via Bluetooth) to create a Surround-Sound experience")
                    .setCancelable(true)
                    .setNegativeButton("Return", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });
            AlertDialog alertDialog = alertDialogBuilder.create();
            alertDialog.show();
        }
        return super.onOptionsItemSelected(item);
    }


    private void resetPlayer() {
        int pauseImg = getResources().getIdentifier("@drawable/pause", null, getPackageName());
        currentSong.setText("<none>");
        seekBar.setEnabled(false);
        playPauseButton.setEnabled(false);
        stopButton.setEnabled(false);
        stopButton.setVisibility(View.INVISIBLE);
        playPauseButton.setVisibility(View.INVISIBLE);
        playPauseButton.setImageDrawable(ResourcesCompat.getDrawable(getResources(), pauseImg, null));
        mediaPlayer.reset();
    }

    public void setSourceToArray(byte[] buf) {
        try {
            // create temp file that will hold byte array
            File tmpFile = File.createTempFile("music", "ogg", getCacheDir());
            tmpFile.deleteOnExit();
            FileOutputStream fos = new FileOutputStream(tmpFile);
            fos.write(buf);
            fos.close();
            FileInputStream fis = new FileInputStream(tmpFile);
            setSource(fis.getFD());
        } catch (IOException e) {}
    }

    // must be called with Uri or FileDescriptor
    private void setSource(Object thing) {
        final String displayText;
        final Uri uri;
        if (thing instanceof Uri) {
            uri = (Uri) thing;
            displayText = uri.getLastPathSegment();
        } else {
            displayText = "<from receiver>";
            uri = null;
        }
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
                currentSong.setText(displayText);
                seekBar.setMax(mediaPlayer.getDuration());
                seekBar.setEnabled(true);
                playPauseButton.setEnabled(true);
                stopButton.setEnabled(true);
                stopButton.setVisibility(View.VISIBLE);
                playPauseButton.setVisibility(View.VISIBLE);
                Toast.makeText(getApplicationContext(), "music loaded", Toast.LENGTH_SHORT).show();
                mp.start();
                setPlaying(false);
            }
        });
        try {
            if (uri != null) {
                mediaPlayer.setDataSource(getApplicationContext(), uri);
                serverBTConn.enqueue(uri);
            } else {
                mediaPlayer.setDataSource((FileDescriptor) thing);
            }
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "invalid music file", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "invalid data source");
            return;
        }
        mediaPlayer.prepareAsync();
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
                resetPlayer();
                setSource(uri);
            }
        } else if (requestCode == BluetoothConnection.REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            // TODO: ?
        } else if (requestCode == BluetoothConnection.REQUEST_ENABLE_BT_DISCOVERABLE && resultCode == Activity.RESULT_CANCELED) {
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

    public void sendFile(Uri uri, DataOutputStream os) throws IOException {
        Cursor cursor = getContentResolver().query(uri, null, null, null, null, null);
        int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
        cursor.moveToFirst();
        long fileSize = cursor.getLong(sizeIndex);
        if (fileSize > Integer.MAX_VALUE) {
            throw new IOException("file too large");
        }
        Log.i(TAG, "sending file...");
        os.writeInt((int) fileSize);
        try (BufferedInputStream bs = new BufferedInputStream(getContentResolver().openInputStream(uri))){
            final int bufferSize = 2048;
            byte[] buffer = new byte[bufferSize];

            int len;
            while ((len = bs.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
            os.flush();
        }
        Log.i(TAG, "done sending file");
    }
}
