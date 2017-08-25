package com.liuxf.qrscanner.decoding;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.liuxf.qrscanner.CaptureActivity;
import com.liuxf.qrscanner.camera.CameraManager;
import com.liuxf.qrscanner.camera.PlanarYUVLuminanceSource;

import java.util.Hashtable;

final class DecodeHandler extends Handler {

  private static final String TAG = DecodeHandler.class.getSimpleName();
  private final CaptureActivity activity;
  private final MultiFormatReader multiFormatReader;

  DecodeHandler(CaptureActivity activity, Hashtable<DecodeHintType, Object> hints) {
    multiFormatReader = new MultiFormatReader();
    multiFormatReader.setHints(hints);
    this.activity = activity;
  }

  @Override
  public void handleMessage(Message message) {
    switch (message.what) {
      case CaptureActivityHandler.DECDOE:
        decode((byte[]) message.obj, message.arg1, message.arg2);
        break;
      case CaptureActivityHandler.QUIT:
        Looper.myLooper().quit();
        break;
    }
  }

  /**
   * Decode the data within the viewfinder rectangle, and time how long it took. For efficiency,
   * reuse the same reader objects from one decode to the next.
   *
   * @param data   The YUV preview frame.
   * @param width  The width of the preview frame.
   * @param height The height of the preview frame.
   */
  private void decode(byte[] data, int width, int height) {
    long start = System.currentTimeMillis();
    Result rawResult = null;


    System.out.println("console:decode-activity.type=" + this.activity.type);
    //modify here
    byte[] rotatedData = new byte[data.length];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++)
        rotatedData[x * height + height - y - 1] = data[x + y * width];
    }
    int tmp = width; // Here we are swapping, that's the difference to #11
    width = height;
    height = tmp;
    System.out.println("console:decode-data.length=" + data.length + ";width=" + width + ";height=" + height);

    PlanarYUVLuminanceSource source = CameraManager.get().buildLuminanceSource(rotatedData, width, height);
    System.out.println("console:decode-source.height=" + source.getDataHeight() + ";width=" + source.getWidth());

    BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
    try {
      rawResult = multiFormatReader.decodeWithState(bitmap);
    } catch (ReaderException re) {
      // continue
    } finally {
      multiFormatReader.reset();
    }

    if (rawResult != null) {
      long end = System.currentTimeMillis();
      Log.d(TAG, "Found barcode (" + (end - start) + " ms):\n" + rawResult.toString());
      Message message = Message.obtain(activity.getHandler(), CaptureActivityHandler.DECDOE_SUCCEEDED, rawResult);
      Bundle bundle = new Bundle();
      bundle.putParcelable(DecodeThread.BARCODE_BITMAP, source.renderCroppedGreyscaleBitmap());
      message.setData(bundle);
      //Log.d(TAG, "Sending decode succeeded message...");
      message.sendToTarget();
    } else {
      Message message = Message.obtain(activity.getHandler(), CaptureActivityHandler.DECDOE_FAILED);
      message.sendToTarget();
    }

  }

  /**
   * 按正方形裁切图片
   */
  public static Bitmap imageCrop(Bitmap bitmap) {
    int w = bitmap.getWidth(); // 得到图片的宽，高
    int h = bitmap.getHeight();

    // int wh = w > h ? h : w;// 裁切后所取的正方形区域边长

    // int retX = w > h ? (w - h) / 2 : 0;//基于原图，取正方形左上角x坐标
    // int retY = w > h ? 0 : (h - w) / 2;

    Rect rect = CameraManager.get().getCapluseRect();
    int wh = rect.width();

    int retX = (w > h ? h : w) / 2 - wh / 2;//真正的宽
    int retY = (w > h ? w : h) / 2 - wh / 2;//真正的高

    //retX和retY匹配bitmap的宽高
    int temp = retX;
    retX = w > h ? retY : retX;
    retY = w > h ? temp : retY;

    // 下面这句是关键
    return Bitmap.createBitmap(bitmap, retX, retY, wh, wh, null, false);
  }
}
