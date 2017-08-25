package com.liuxf.qrscanner;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.liuxf.qrscanner.camera.CameraManager;
import com.liuxf.qrscanner.decoding.CaptureActivityHandler;
import com.liuxf.qrscanner.decoding.InactivityTimer;
import com.liuxf.qrscanner.view.ViewfinderView;

import java.io.IOException;
import java.util.Vector;

public class CaptureActivity extends Activity implements Callback {

  public static CaptureActivity instance = null;
  private CaptureActivityHandler handler;
  private ViewfinderView viewfinderView;
  private boolean hasSurface;
  private Vector<BarcodeFormat> decodeFormats;
  private String characterSet;
  private InactivityTimer inactivityTimer;
  private MediaPlayer mediaPlayer;
  private boolean playBeep;
  private static final float BEEP_VOLUME = 0.10f;
  private boolean vibrate;
  public static int type;
  public Bitmap mResBitmap;
  public static boolean isCallback = true;

  public static final String CAPSULE = "jiaonang";
  public static final String SCAN_EXPRESS = "expressList";

  /**
   * Called when the activity is first created.
   */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    //去除标题栏
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.activity_capture);
    instance = this;
    CameraManager.init(getApplication());
    viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
    TextView textView = (TextView) findViewById(R.id.textview_title);
    if (this.type == 1) {
      viewfinderView.setActionType(CAPSULE);
      textView.setText(R.string.scan_capsule);
    }
    Button mButtonBack = (Button) findViewById(R.id.button_back);
    mButtonBack.setOnClickListener(new OnClickListener() {

      @Override
      public void onClick(View v) {
        CaptureActivity.this.finish();

      }
    });
    hasSurface = false;

    inactivityTimer = new InactivityTimer(this);
  }

  @Override
  protected void onResume() {
    System.out.println("console:CaptureActivity-onResume");
    super.onResume();
    SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
    SurfaceHolder surfaceHolder = surfaceView.getHolder();
    if (hasSurface) {
      initCamera(surfaceHolder);
    } else {
      surfaceHolder.addCallback(this);
      surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }
    decodeFormats = null;
    characterSet = null;

    playBeep = true;
    AudioManager audioService = (AudioManager) getSystemService(AUDIO_SERVICE);
    if (audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
      playBeep = false;
    }
    initBeepSound();
    vibrate = true;

  }

  @Override
  protected void onPause() {
    super.onPause();
    if (handler != null) {
      handler.quitSynchronously();
      handler = null;
    }
    CameraManager.get().closeDriver();
  }

  @Override
  protected void onDestroy() {
    inactivityTimer.shutdown();
    super.onDestroy();
  }

  /**
   * ����ɨ����
   *
   * @param result
   * @param barcode
   */
  public void handleDecode(Result result, Bitmap barcode) {
    System.out.println("console:CaptureActivity-handleDecode");
   if (null != barcode) {
      inactivityTimer.onActivity();
      playBeepSoundAndVibrate();
      String resultString = result.getText();
      BarcodeFormat format = result.getBarcodeFormat();
      System.out.println("console:resultString = " + resultString);
      if (resultString.equals("")) {
        Toast.makeText(CaptureActivity.this, "Scan failed!", Toast.LENGTH_SHORT).show();
      } else {
        Intent resultIntent = new Intent();
        Bundle bundle = new Bundle();
        bundle.putString("SCAN_RESULT", resultString);
        bundle.putString("SCAN_RESULT_FORMAT", format.toString());
        resultIntent.putExtras(bundle);
        setResult(Activity.RESULT_OK, resultIntent);
      }
      finish();
    }

    System.out.println("console:CaptureActivity-handleDecode---------------------");

  }

  private void initCamera(SurfaceHolder surfaceHolder) {
    try {
      CameraManager.get().openDriver(surfaceHolder);
    } catch (IOException ioe) {
      return;
    } catch (RuntimeException e) {
      return;
    }
    if (handler == null) {
      handler = new CaptureActivityHandler(this, decodeFormats, characterSet);
    }
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width,
                             int height) {

  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    if (!hasSurface) {
      hasSurface = true;
      initCamera(holder);
    }

  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    hasSurface = false;

  }

  public ViewfinderView getViewfinderView() {
    return viewfinderView;
  }

  public Handler getHandler() {
    return handler;
  }

  public void drawViewfinder() {
    viewfinderView.drawViewfinder();

  }

  private void initBeepSound() {
    if (playBeep && mediaPlayer == null) {
      setVolumeControlStream(AudioManager.STREAM_MUSIC);
      mediaPlayer = new MediaPlayer();
      mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
      mediaPlayer.setOnCompletionListener(beepListener);

      AssetFileDescriptor file = getResources().openRawResourceFd(
        R.raw.beep);
      try {
        mediaPlayer.setDataSource(file.getFileDescriptor(),
          file.getStartOffset(), file.getLength());
        file.close();
        mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
        mediaPlayer.prepare();
      } catch (IOException e) {
        mediaPlayer = null;
      }
    }
  }

  private static final long VIBRATE_DURATION = 200L;

  private void playBeepSoundAndVibrate() {
    if (playBeep && mediaPlayer != null) {
      mediaPlayer.start();
    }
    if (vibrate) {
      Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
      vibrator.vibrate(VIBRATE_DURATION);
    }
  }

  /**
   * When the beep has finished playing, rewind to queue up another one.
   */
  private final OnCompletionListener beepListener = new OnCompletionListener() {
    public void onCompletion(MediaPlayer mediaPlayer) {
      mediaPlayer.seekTo(0);
    }
  };

}
