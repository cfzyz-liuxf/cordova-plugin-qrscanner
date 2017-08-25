package com.liuxf.qrscanner.decoding;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;

import com.liuxf.qrscanner.CaptureActivity;
import com.liuxf.qrscanner.camera.CameraManager;
import com.liuxf.qrscanner.view.ViewfinderResultPointCallback;

import java.util.Vector;

public final class CaptureActivityHandler extends Handler {

  private static final String TAG = CaptureActivityHandler.class.getSimpleName();

  private final CaptureActivity activity;
  private final DecodeThread decodeThread;
  private State state;

  private enum State {
    PREVIEW,
    SUCCESS,
    DONE
  }

  protected static final int DECDOE = 1;
  protected static final int DECDOE_FAILED = 2;
  protected static final int DECDOE_SUCCEEDED = 3;
  protected static final int SCAN_PRODUCT = 4;
  protected static final int LAUNCH_PRODUCT_QUERY = 5;
  protected static final int QUIT = 6;
  protected static final int RESTART_PREVIEW = 7;
  protected static final int RETURN_SCAN_RESULT = 8;
  protected static final int AUTO_FOCUS = 9;

  public CaptureActivityHandler(CaptureActivity activity, Vector<BarcodeFormat> decodeFormats,
                                String characterSet) {
    this.activity = activity;
    decodeThread = new DecodeThread(activity, decodeFormats, characterSet,
      new ViewfinderResultPointCallback(activity.getViewfinderView()));
    decodeThread.start();
    state = State.SUCCESS;
    // Start ourselves capturing previews and decoding.
    CameraManager.get().startPreview();
    restartPreviewAndDecode();
  }

  @Override
  public void handleMessage(Message message) {
    System.out.println("CaptureActiovityHandler:handleMessage;what=" + message.what);
    switch (message.what) {
      case AUTO_FOCUS:
        //Log.d(TAG, "Got auto-focus message");
        // When one auto focus pass finishes, start another. This is the closest thing to
        // continuous AF. It does seem to hunt a bit, but I'm not sure what else to do.
        if (state == State.PREVIEW) {
          CameraManager.get().requestAutoFocus(this, AUTO_FOCUS);
        }
        break;
      case RESTART_PREVIEW:
        Log.d(TAG, "Got restart preview message");
        restartPreviewAndDecode();
        break;
      case DECDOE_SUCCEEDED:
        Log.d(TAG, "Got decode succeeded message");
        state = State.SUCCESS;
        Bundle bundle = message.getData();

        /***********************************************************************/
        Bitmap barcode = bundle == null ? null :
          (Bitmap) bundle.getParcelable(DecodeThread.BARCODE_BITMAP);

        activity.handleDecode((Result) message.obj, barcode);
        break;
      case DECDOE_FAILED:
        // We're decoding as fast as possible, so when one decode fails, start another.
        state = State.PREVIEW;
        CameraManager.get().requestPreviewFrame(decodeThread.getHandler(), DECDOE);
        break;
      case RETURN_SCAN_RESULT:
        Log.d(TAG, "Got return scan result message");
        activity.setResult(Activity.RESULT_OK, (Intent) message.obj);
        activity.finish();
        break;
      case LAUNCH_PRODUCT_QUERY:
        Log.d(TAG, "Got product query message");
        String url = (String) message.obj;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        activity.startActivity(intent);
        break;
    }
  }

  public void quitSynchronously() {
    state = State.DONE;
    CameraManager.get().stopPreview();
    Message quit = Message.obtain(decodeThread.getHandler(), QUIT);
    quit.sendToTarget();
    try {
      decodeThread.join();
    } catch (InterruptedException e) {
      // continue
    }

    // Be absolutely sure we don't send any queued up messages
    removeMessages(DECDOE_SUCCEEDED);
    removeMessages(DECDOE_FAILED);
  }

  private void restartPreviewAndDecode() {
    if (state == State.SUCCESS) {
      state = State.PREVIEW;
      System.out.println("console:CaptureActivity-restartPreviewAndDecode;R.id.decode=" + DECDOE);
      CameraManager.get().requestPreviewFrame(decodeThread.getHandler(), DECDOE);
      CameraManager.get().requestAutoFocus(this, AUTO_FOCUS);
      activity.drawViewfinder();
    }
  }

  public void restartDecode() {
    System.out.println("console:CaptureActivity-restartDecode;R.id.decode=" + DECDOE);
    CameraManager.get().requestPreviewFrame(decodeThread.getHandler(), DECDOE);
    CameraManager.get().requestAutoFocus(this, AUTO_FOCUS);
    activity.drawViewfinder();
  }

}
