package com.example.soundbytes;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Handler;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private final int RECORD_AUDIO_PERMISSION_CODE = 1;

    private Handler mainHandler;
    private RecordSoundRunnable rsRunnable;

    private ProgressBar pb_record;
    private TextView tv_recordUpdate;
    private TextView tv_recordCancel;
    private Button b_recordCancel;

    private int m_numOfSamples;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainHandler = new Handler();

        pb_record = findViewById(R.id.progressBar_main_record);
        tv_recordUpdate = findViewById(R.id.textView_main_recordUpdate);
        tv_recordCancel = findViewById(R.id.textView_main_recordCancel);
        b_recordCancel = findViewById(R.id.button_main_recordCancel);
    }

    /**
     *  Button Functions
     */
    public void startRecordingSamples(View view) {
        /** determine number of samples to take */
        int id = view.getId();
        switch(id)
        {
            case R.id.button_main_record1:
                m_numOfSamples = 1;
                break;
            case R.id.button_main_record25:
                m_numOfSamples = 25;
                break;
            case R.id.button_main_record100:
                m_numOfSamples = 100;
                break;
        }

        // check if permissions are already granted
        if (checkPermissionFromDevice(RECORD_AUDIO_PERMISSION_CODE)) {
            openConfirmDialog();
        }
        // if permissions aren't granted, request them
        else {
            requestPermissions(RECORD_AUDIO_PERMISSION_CODE);
        }
    }

    public void stopRecordingSamples(View view) {
        tv_recordCancel.setVisibility(View.VISIBLE);
        rsRunnable.stopRecordingSound();
    }

    /**
     *  Confirm to start recording and name files.
     */
    public void openConfirmDialog() {
        AlertDialog.Builder popUp = new AlertDialog.Builder(this);
        popUp.setTitle("Start Recording?");
        popUp.setMessage("Enter the file name(s)");

        final EditText input_name = new EditText(this);
        popUp.setView(input_name);

        popUp.setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                rsRunnable = new RecordSoundRunnable(input_name.getText().toString());
                new Thread(rsRunnable).start();
                // disable all buttons
            }
        });

        popUp.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
            }
        });

        popUp.show();
    }

    /**
     * List of Permission methods
     */
    private boolean checkPermissionFromDevice(int permissions) {

        switch(permissions){
            case RECORD_AUDIO_PERMISSION_CODE: {
                // int variables will be 0 if permissions are not granted already
                int write_external_storage_result =
                        ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                int record_audio_result =
                        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);

                // returns true if both permissions are already granted
                return write_external_storage_result == PackageManager.PERMISSION_GRANTED &&
                        record_audio_result == PackageManager.PERMISSION_GRANTED;
            }
            default:
                return false;
        }
    }
    private void requestPermissions(int permissions) {

        switch(permissions){
            case RECORD_AUDIO_PERMISSION_CODE: {
                // used to pass what permissions were requested
                String[] permissionsRequested = {
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.RECORD_AUDIO};

                // requests all necessary permissions
                ActivityCompat.requestPermissions(this, permissionsRequested, permissions);
                break;
            }
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        boolean permissionGranted = false;

        switch (requestCode) {
            case RECORD_AUDIO_PERMISSION_CODE: {
                if (grantResults.length > 0) {
                    for (int i : grantResults) {
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED)
                            permissionGranted = true;
                    }

                    if (permissionGranted) {
                        Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
                        openConfirmDialog();
                    } else
                        Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
                }
                else
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();

                break;
            }
        }
    }

    /**
     * CLASS: records audio
     */
    class RecordSoundRunnable implements Runnable {

        /** MEMBER VARIABLES */
        private static final int RECORDER_SOURCE = MediaRecorder.AudioSource.UNPROCESSED;
        private static final int RECORDER_SAMPLERATE = 44100;
        private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
        private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
        private int BufferElementsToRec = 1024;  // want to play 2048 (2K) since 2 bytes we use only 1024
        private int BytesPerElement = 2;        // 2 bytes in 16bit format

        private String m_fileName;
        private AudioRecord recorder;
        private volatile boolean isRecording;

        /** CONSTRUCTOR */
        RecordSoundRunnable(String fileName) {
            this.m_fileName = fileName;
            int bufferSize = AudioRecord.getMinBufferSize(
                    RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
            recorder = new AudioRecord(
                    RECORDER_SOURCE, RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING, bufferSize);
            isRecording = true;
        }

        /** RUN */
        @Override
        public void run() {

            /** show progress bar and cancel button */
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    pb_record.setVisibility(View.VISIBLE);
                    b_recordCancel.setVisibility(View.VISIBLE);
                }
            });

            /** initial buffer of 5 secs */
            try {
                Thread.sleep(5000);
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            /** take specified number of samples */
            for (int i = 0; i < m_numOfSamples; i++) {

                /** check to see if cancel button has been used */
                if (!isRecording) {
                    /** hide progress bar and cancel button */
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            pb_record.setVisibility(View.INVISIBLE);
                            b_recordCancel.setVisibility(View.INVISIBLE);
                            tv_recordCancel.setVisibility(View.INVISIBLE);
                        }
                    });
                    return;
                }

                try {
                    /** creates new folders in storage if they do not exist */
                    File pathParent = new File( Environment.getExternalStoragePublicDirectory("Sound Bytes") + "/");
                    if (!pathParent.exists())
                        pathParent.mkdir();
                    File pathChild = new File(pathParent + "/" + m_fileName + "/");
                    if (!pathChild.exists())
                        pathChild.mkdir();

                    /** creates file path */
                    String fileName = getFileName();
                    String filePath = pathChild + "/" + fileName;
                    FileOutputStream os = new FileOutputStream(filePath + ".pcm");

                    /** unknown */
                    short soundData[] = new short[BufferElementsToRec];

                    /** starts recording for 3 secs */
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            tv_recordUpdate.setVisibility(View.VISIBLE);
                        }
                    });
                    recorder.startRecording();
                    long a = System.currentTimeMillis();    // start time
                    while (true) {
                        recorder.read(soundData, 0, BufferElementsToRec);
                        // writes the data to file from buffer
                        byte bufferData[] = shortToByte(soundData);
                        // stores the voice buffer
                        os.write(bufferData, 0, BufferElementsToRec * BytesPerElement);

                        long b =  System.currentTimeMillis();   // end time
                        if(b - a >= 3000) // 3 secs have passed
                            break;
                    }

                    /** stops recording */
                    recorder.stop();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            tv_recordUpdate.setVisibility(View.INVISIBLE);
                        }
                    });
                    os.close();

                    /** buffer of 1 sec in between taking samples */
                    Thread.sleep(1000);

                    /** converts pcm file to wav */
                    File f1 = new File(filePath + ".pcm"); // The location of your PCM file
                    File f2 = new File(filePath + ".wav"); // The location where you want your WAV file
                    try {
                        rawToWave(f1, f2);
                        f1.delete();
                    } catch (IOException e) {
                        e.printStackTrace();
                        f1.delete();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            /** hide progress bar and cancel button */
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    pb_record.setVisibility(View.INVISIBLE);
                    b_recordCancel.setVisibility(View.INVISIBLE);
                }
            });

            /** cleanup */
            recorder.release();
            recorder = null;
        }

        /** MEMBER FUNCTIONS */

        /** names file */
        private String getFileName() {
            Date time = new Date(System.currentTimeMillis());
            return (m_fileName + " " + time);
        }

        /** setter method to change isRecording value to false */
        private void stopRecordingSound() {
            isRecording = false;
        }

        /** converts short to byte */
        private byte[] shortToByte(short[] soundData) {
            int shortArrSize = soundData.length;
            byte[] bytes = new byte[shortArrSize * 2];
            for (int i = 0; i < shortArrSize; i++) {
                bytes[i * 2] = (byte) (soundData[i] & 0x00FF);
                bytes[(i * 2) + 1] = (byte) (soundData[i] >> 8);
                soundData[i] = 0;
            }
            return bytes;
        }

        /** PCM to WAV */
        private void rawToWave(final File rawFile, final File waveFile) throws IOException {

            byte[] rawData = new byte[(int) rawFile.length()];
            DataInputStream input = null;
            try {
                input = new DataInputStream(new FileInputStream(rawFile));
                input.read(rawData);
            } finally {
                if (input != null) {
                    input.close();
                }
            }

            DataOutputStream output = null;
            try {
                output = new DataOutputStream(new FileOutputStream(waveFile));
                // WAVE header
                // see http://ccrma.stanford.edu/courses/422/projects/WaveFormat/
                writeString(output, "RIFF"); // chunk id
                writeInt(output, 36 + rawData.length); // chunk size
                writeString(output, "WAVE"); // format
                writeString(output, "fmt "); // subchunk 1 id
                writeInt(output, 16); // subchunk 1 size
                writeShort(output, (short) 1); // audio format (1 = PCM)
                writeShort(output, (short) 1); // number of channels
                writeInt(output, 44100); // sample rate
                writeInt(output, RECORDER_SAMPLERATE * 2); // byte rate
                writeShort(output, (short) 2); // block align
                writeShort(output, (short) 16); // bits per sample
                writeString(output, "data"); // subchunk 2 id
                writeInt(output, rawData.length); // subchunk 2 size
                // Audio data (conversion big endian -> little endian)
                short[] shorts = new short[rawData.length / 2];
                ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
                ByteBuffer bytes = ByteBuffer.allocate(shorts.length * 2);
                for (short s : shorts) {
                    bytes.putShort(s);
                }

                output.write(fullyReadFileToBytes(rawFile));
            } finally {
                if (output != null) {
                    output.close();
                }
            }
        }
        byte[] fullyReadFileToBytes(File f) throws IOException {
            int size = (int) f.length();
            byte bytes[] = new byte[size];
            byte tmpBuff[] = new byte[size];
            FileInputStream fis= new FileInputStream(f);
            try {

                int read = fis.read(bytes, 0, size);
                if (read < size) {
                    int remain = size - read;
                    while (remain > 0) {
                        read = fis.read(tmpBuff, 0, remain);
                        System.arraycopy(tmpBuff, 0, bytes, size - remain, read);
                        remain -= read;
                    }
                }
            }  catch (IOException e){
                throw e;
            } finally {
                fis.close();
            }

            return bytes;
        }
        private void writeInt(final DataOutputStream output, final int value) throws IOException {
            output.write(value >> 0);
            output.write(value >> 8);
            output.write(value >> 16);
            output.write(value >> 24);
        }
        private void writeShort(final DataOutputStream output, final short value) throws IOException {
            output.write(value >> 0);
            output.write(value >> 8);
        }
        private void writeString(final DataOutputStream output, final String value) throws IOException {
            for (int i = 0; i < value.length(); i++) {
                output.write(value.charAt(i));
            }
        }

    } // END OF RECORD_SOUND_RUNNABLE

} // END OF MAIN_ACTIVITY
