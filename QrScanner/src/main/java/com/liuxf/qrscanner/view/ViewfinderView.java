package com.liuxf.qrscanner.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Region.Op;
import android.graphics.Typeface;
import android.text.Layout.Alignment;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import com.google.zxing.ResultPoint;
import com.liuxf.qrscanner.CaptureActivity;
import com.liuxf.qrscanner.R;
import com.liuxf.qrscanner.camera.CameraManager;

import java.util.Collection;
import java.util.HashSet;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder
 * rectangle and partial transparency outside it, as well as the laser scanner
 * animation and result points.
 */
public final class ViewfinderView extends View {
  private static final String TAG = "log";

  /**
   * 刷新界面的时间
   */
  private static final long ANIMATION_DELAY = 5L;
  private static final int OPAQUE = 0xFF;

  /**
   * 四个绿色边角对应的宽度
   */
  private static final int CORNER_WIDTH = 4;
  /**
   * 扫描框中的中间线的宽度
   */
  private static final int MIDDLE_LINE_WIDTH = 6;

  /**
   * 扫描框中的中间线的与扫描框左右的间隙
   */
  private static final int MIDDLE_LINE_PADDING = 4;

  /**
   * 中间那条线每次刷新移动的距离
   */
  private static final int SPEEN_DISTANCE = 15;

  /**
   * 字体大小
   */
  private static final int TEXT_SIZE = 16;
  /**
   * 字体距离扫描框下面的距离
   */
  private static final int TEXT_PADDING_TOP = 30;

  private String actionType = CaptureActivity.CAPSULE;

  private boolean isFirst;
  private Rect frame, src, dst;
  private int maskColor, resultColor, resultPointColor, rectColor;
  private float density;
  private String scanText;
  private Bitmap stepBitmap, resultBitmap;
  private int slideTop, ScreenRate;
  private HashSet<ResultPoint> possibleResultPoints;
  private Paint paint;
  private Path stepPath;
  private Collection<ResultPoint> lastPossibleResultPoints;

  private Context context;

  public void setActionType(String type) {
    this.actionType = type;
  }

  public ViewfinderView(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.context = context;
    maskColor = context.getResources().getColor(R.color.viewfinder_mask);
    resultColor = context.getResources().getColor(R.color.result_view);
    resultPointColor = context.getResources().getColor(R.color.possible_result_points);
    rectColor = context.getResources().getColor(R.color.rect_blue);
    density = context.getResources().getDisplayMetrics().density;
    // 将像素转换成dp
    ScreenRate = (int) (20 * density);
    stepBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.scan_step);
  }

  @Override
  public void onDraw(Canvas canvas) {
    if (!isFirst) {
      isFirst = true;
      init(canvas);
    }
    drawRectScanBg(canvas);
    doDraw(canvas);
    // 只刷新扫描框的内容，其他地方不刷新
    postInvalidateDelayed(ANIMATION_DELAY, frame.left, frame.top, frame.right, frame.bottom);
  }

  private void doDraw(Canvas canvas) {
    drawText(canvas);
    paint.setColor(resultBitmap != null ? resultColor : maskColor);
    if (resultBitmap != null) {
      // Draw the opaque result bitmap over the scanning rectangle
      paint.setAlpha(OPAQUE);
      canvas.drawBitmap(resultBitmap, frame.left, frame.top, paint);
    } else {
      // �����м����,ÿ��ˢ�½��棬�м���������ƶ�SPEEN_DISTANCE
      slideTop += SPEEN_DISTANCE;
      if (slideTop - src.height() >= frame.bottom) {
        slideTop = frame.top;
      }
      dst.bottom = slideTop;
      dst.top = slideTop - src.height();
      drawScanStep(canvas, src, dst, stepPath);

      Collection<ResultPoint> currentPossible = possibleResultPoints;
      Collection<ResultPoint> currentLast = lastPossibleResultPoints;
      if (currentPossible.isEmpty()) {
        lastPossibleResultPoints = null;
      } else {
        possibleResultPoints = new HashSet<ResultPoint>(5);
        lastPossibleResultPoints = currentPossible;
        paint.setAlpha(OPAQUE);
        paint.setColor(resultPointColor);
        for (ResultPoint point : currentPossible) {
          canvas.drawCircle(frame.left + point.getX(), frame.top + point.getY(), 6.0f, paint);
        }
      }
      if (currentLast != null) {
        paint.setAlpha(OPAQUE / 2);
        paint.setColor(resultPointColor);
        for (ResultPoint point : currentLast) {
          canvas.drawCircle(frame.left + point.getX(), frame.top + point.getY(), 3.0f, paint);
        }
      }
    }
  }

  private void init(Canvas canvas) {
    paint = new Paint();
    paint.setAntiAlias(true);
    stepPath = new Path();
    // 中间的扫描框，你要修改扫描框的大小，去CameraManager里面修改
    frame = CameraManager.get().getFramingRect();
    int descId = actionType.equals(CaptureActivity.SCAN_EXPRESS) ? R.string.scan_text : R.string.scan_text;
    scanText = context.getResources().getString(descId);
    // 设置裁剪的矩形
    stepPath.addRect(frame.left, frame.top, frame.right, frame.bottom, Path.Direction.CCW);
    slideTop = frame.top;
    possibleResultPoints = new HashSet<ResultPoint>(5);
    src = new Rect(0, 0, stepBitmap.getWidth(), stepBitmap.getHeight());
    dst = new Rect(frame.left, frame.top, frame.right, frame.top);
  }

  private void drawRectScanBg(Canvas canvas) {
    // 获取画布大小
    int width = canvas.getWidth();
    int height = canvas.getHeight();

    paint.setColor(maskColor);
    // 画出扫描框外面的阴影部分，共四个部分，扫描框的上面到屏幕上面，扫描框的下面到屏幕下面
    // 扫描框的左边面到屏幕左边，扫描框的右边到屏幕右边
    canvas.drawRect(0, 0, width, frame.top, paint);
    canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
    canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
    canvas.drawRect(0, frame.bottom + 1, width, height, paint);

    draw4Rect(canvas);
  }

  private void draw4Rect(Canvas canvas) {
    // 画扫描框边上的角，总共8个部分
    paint.setColor(rectColor);
    canvas.drawRect(frame.left, frame.top, frame.left + ScreenRate, frame.top + CORNER_WIDTH, paint);
    canvas.drawRect(frame.left, frame.top, frame.left + CORNER_WIDTH, frame.top + ScreenRate, paint);
    canvas.drawRect(frame.right - ScreenRate, frame.top, frame.right, frame.top + CORNER_WIDTH, paint);
    canvas.drawRect(frame.right - CORNER_WIDTH, frame.top, frame.right, frame.top + ScreenRate, paint);
    canvas.drawRect(frame.left, frame.bottom - CORNER_WIDTH, frame.left + ScreenRate, frame.bottom, paint);
    canvas.drawRect(frame.left, frame.bottom - ScreenRate, frame.left + CORNER_WIDTH, frame.bottom, paint);
    canvas.drawRect(frame.right - ScreenRate, frame.bottom - CORNER_WIDTH, frame.right, frame.bottom, paint);
    canvas.drawRect(frame.right - CORNER_WIDTH, frame.bottom - ScreenRate, frame.right, frame.bottom, paint);
  }

  private void drawCapsuleScanBg(Canvas canvas) {
    // 获取画布大小
    int width = canvas.getWidth();
    int height = canvas.getHeight();

    // 画阴影背景
    canvas.save();
    canvas.clipRect(0, 0, width, height);
    Path path = new Path();
    // 设置裁剪的圆心，半径
    path.addCircle(frame.left + frame.width() / 2 + CORNER_WIDTH, frame.top + frame.width() / 2 + CORNER_WIDTH, frame.width() / 2, Path.Direction.CCW);
    // 裁剪画布，并设置其填充方式
    canvas.clipPath(path, Op.DIFFERENCE);
    canvas.drawColor(maskColor);
    canvas.restore();

    draw4Rect(canvas);

    // 画白色圆边，边宽3
    paint.setColor(Color.WHITE);
    paint.setStyle(Style.STROKE);
    paint.setStrokeWidth(MIDDLE_LINE_WIDTH / 2);
    canvas.drawCircle(frame.left + frame.width() / 2 + CORNER_WIDTH, frame.top + frame.width() / 2 + CORNER_WIDTH, frame.width() / 2, paint);
    paint.setStyle(Style.FILL);
  }

  private void drawText(Canvas canvas) {
    canvas.save();
    // 画文字提示
    TextPaint textPaint = new TextPaint();
    textPaint.setColor(Color.WHITE);
    textPaint.setTextSize(TEXT_SIZE * density);
    textPaint.setAlpha(0x40);
    textPaint.setTypeface(Typeface.create("System", Typeface.BOLD));

    StaticLayout layout = new StaticLayout(scanText, textPaint, frame.width(), Alignment.ALIGN_CENTER, 1.0F, 0.0F, true);
    canvas.translate(frame.left, (int) (frame.bottom + (float) TEXT_PADDING_TOP * density));
    layout.draw(canvas);
    canvas.restore();
  }

  /**
   * 绘制扫描步进效果
   *
   * @param canvas
   */
  protected void drawScanStep(Canvas canvas, Rect src, Rect dst, Path path) {
    // 绘制步进效果
    canvas.save();
    // 裁剪画布，并设置其填充方式
    canvas.clipPath(path, Op.REPLACE);
    canvas.drawBitmap(stepBitmap, src, dst, null);
    canvas.restore();
  }

  public void drawViewfinder() {
    resultBitmap = null;
    invalidate();
  }

  public void addPossibleResultPoint(ResultPoint point) {
    possibleResultPoints.add(point);
  }

}
