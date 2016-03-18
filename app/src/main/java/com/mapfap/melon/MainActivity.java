package com.mapfap.melon;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import ca.uol.aig.fftpack.RealDoubleFFT;


public class MainActivity extends Activity implements OnClickListener{

    private static final int SAMPLE_RATE = 8000;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, AUDIO_ENCODING);

    AudioRecord recorder;
    private RealDoubleFFT transformer;
    Button startStopButton;
    TextView console;
    boolean started = false;
    boolean CANCELLED_FLAG = false;
    RecordingTask recordingTask;
    ImageView spectrumView;
    Bitmap spectrumBitmap;
    Canvas spectrumCanvas;
    Paint spectrumPainter;
    LinearLayout mainLayout;

    int screenWidth;
    int maxAmplitude;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DisplayMetrics displaymetrics = new DisplayMetrics();
        this.getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        screenWidth = displaymetrics.widthPixels;
        transformer = new RealDoubleFFT(BUFFER_SIZE);
        setupLayout();
    }

    private void setupLayout() {
        mainLayout = new LinearLayout(this);
        mainLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        spectrumView = new ImageView(this);
        spectrumBitmap = Bitmap.createBitmap(BUFFER_SIZE, BUFFER_SIZE, Bitmap.Config.ARGB_8888);
        LinearLayout.LayoutParams layoutParams_imageViewScale = null;
        spectrumPainter = new Paint();
        spectrumCanvas = new Canvas(spectrumBitmap);
        spectrumView.setImageBitmap(spectrumBitmap);

        LinearLayout.LayoutParams layoutParams_imageViewDisplaySpectrum=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        spectrumView.setLayoutParams(layoutParams_imageViewDisplaySpectrum);
        layoutParams_imageViewScale= new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ((MarginLayoutParams) layoutParams_imageViewScale).setMargins(0, 0, 0, 0);

        mainLayout.addView(spectrumView);

        startStopButton = new Button(this);
        startStopButton.setText("Start");
        startStopButton.setOnClickListener(this);
        startStopButton.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        mainLayout.addView(startStopButton);

        console = new TextView(this);
        console.setText(">> ");
        console.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        mainLayout.addView(console);

        setContentView(mainLayout);
    }

    private class RecordingTask extends AsyncTask<Void, double[], Boolean> {

        @Override
        protected Boolean doInBackground(Void... params) {
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, AUDIO_ENCODING, BUFFER_SIZE);
            int bufferReadResult;
            short[] buffer = new short[BUFFER_SIZE];
            double[] toTransform = new double[BUFFER_SIZE];

            if (recorder.getState() != AudioRecord.STATE_INITIALIZED){
                Log.e("Error", "Check Audio Record Permission");
                return true;
            }

            try {
                recorder.startRecording();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
            while (started) {
                if (isCancelled() || (CANCELLED_FLAG)) {
                    started = false;
                    Log.d("doInBackground", "Cancelling the RecordTask");
                    break;
                } else {
                    bufferReadResult = recorder.read(buffer, 0, BUFFER_SIZE);

                    for (int i = 0; i < BUFFER_SIZE && i < bufferReadResult; i++) {
                        toTransform[i] = (double) buffer[i] / 32768.0; // signed 16 bit
                    }
                    transformer.ft(toTransform);
                    publishProgress(toTransform);
                }

            }
            return true;
        }

        protected void drawScale() {
//            spectrumCanvas.drawLine(0, 50, BUFFER_SIZE, 50, spectrumPainter);
//            spectrumCanvas.drawLine(0, 100, BUFFER_SIZE, 100, spectrumPainter);
//            spectrumCanvas.drawLine(0, 150, BUFFER_SIZE, 150, spectrumPainter);
        }

        @Override
        protected void onProgressUpdate(double[]...progress) {
            spectrumCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            drawScale();
            for (int i = 0; i < progress[0].length; i++) {
                if (i < 100) {
                    spectrumPainter.setColor(Color.GREEN);
                }
                else {
                    spectrumPainter.setColor(Color.RED);
                }
                int x = i;
                int downy = 0;
                int upy = (int) (progress[0][i] * 10);

                if (upy > maxAmplitude) {
                    maxAmplitude = upy;
                    console.setText(">> Max Amplitude: " + maxAmplitude + ", Frequency: " + i);
                }

                spectrumCanvas.drawLine(x, downy, x, upy, spectrumPainter);
            }

            spectrumView.invalidate();
        }
        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            try{
                recorder.stop();
            }
            catch(IllegalStateException e){
                e.printStackTrace();
            }

            spectrumCanvas.drawColor(Color.BLACK);
            spectrumView.invalidate();
        }
    }

    public void onClick(View v) {
        if (started) {
            CANCELLED_FLAG = true;
            try{
                recorder.stop();
            }
            catch(IllegalStateException e){
                e.printStackTrace();
            }
            startStopButton.setText("Start");
            spectrumCanvas.drawColor(Color.BLACK);
        } else {
            started = true;
            CANCELLED_FLAG = false;
            startStopButton.setText("Stop");
            maxAmplitude = Integer.MIN_VALUE;
            recordingTask = new RecordingTask();
            recordingTask.execute();
        }

    }

    @Override
    public void onStop(){
        super.onStop();
        stopRecording();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        stopRecording();;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRecording();
    }

    private void stopRecording() {
        try {
            recorder.stop();
        } catch(IllegalStateException e){
            e.printStackTrace();
        }
        recordingTask.cancel(true);
    }
}

