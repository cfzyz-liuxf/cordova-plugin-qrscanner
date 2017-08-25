package com.liuxf.qrscanner.camera;

import android.graphics.Bitmap;

import com.google.zxing.LuminanceSource;

/**
 * This object extends LuminanceSource around an array of YUV data returned from the camera driver,
 * with the option to crop to a rectangle within the full data. This can be used to exclude
 * superfluous pixels around the perimeter and speed up decoding.
 *
 * It works for any pixel format where the Y channel is planar and appears first, including
 * YCbCr_420_SP and YCbCr_422_SP.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class PlanarYUVLuminanceSource extends LuminanceSource {
  private final byte[] yuvData;
  private final int dataWidth;
  private final int dataHeight;
  private final int left;
  private final int top;

  public PlanarYUVLuminanceSource(byte[] yuvData, int dataWidth, int dataHeight, int left, int top,
      int width, int height) {
    super(width, height);

    if (left + width > dataWidth || top + height > dataHeight) {
      throw new IllegalArgumentException("Crop rectangle does not fit within image data.");
    }

    this.yuvData = yuvData;
    this.dataWidth = dataWidth;
    this.dataHeight = dataHeight;
    this.left = left;
    this.top = top;
  }

  @Override
  public byte[] getRow(int y, byte[] row) {
    if (y < 0 || y >= getHeight()) {
      throw new IllegalArgumentException("Requested row is outside the image: " + y);
    }
    int width = getWidth();
    if (row == null || row.length < width) {
      row = new byte[width];
    }
    int offset = (y + top) * dataWidth + left;
    System.arraycopy(yuvData, offset, row, 0, width);
    return row;
  }

  @Override
  public byte[] getMatrix() {
    int width = getWidth();
    int height = getHeight();

    // If the caller asks for the entire underlying image, save the copy and give them the
    // original data. The docs specifically warn that result.length must be ignored.
    if (width == dataWidth && height == dataHeight) {
      return yuvData;
    }

    int area = width * height;
    byte[] matrix = new byte[area];
    int inputOffset = top * dataWidth + left;

    // If the width matches the full width of the underlying data, perform a single copy.
    if (width == dataWidth) {
      System.arraycopy(yuvData, inputOffset, matrix, 0, area);
      return matrix;
    }

    // Otherwise copy one cropped row at a time.
    byte[] yuv = yuvData;
    for (int y = 0; y < height; y++) {
      int outputOffset = y * width;
      System.arraycopy(yuv, inputOffset, matrix, outputOffset, width);
      inputOffset += dataWidth;
    }
    return matrix;
  }

  @Override
  public boolean isCropSupported() {
    return true;
  }

  public int getDataWidth() {
    return dataWidth;
  }

  public int getDataHeight() {
    return dataHeight;
  }

  public Bitmap renderCroppedGreyscaleBitmap() {
    int width = getWidth();
    int height = getHeight();
    int[] pixels = new int[width * height];
    byte[] yuv = yuvData;
    int inputOffset = top * dataWidth + left;

    for (int y = 0; y < height; y++) {
      int outputOffset = y * width;
      for (int x = 0; x < width; x++) {
        int grey = yuv[inputOffset + x] & 0xff;
        pixels[outputOffset + x] = 0xFF000000 | (grey * 0x00010101);
      }
      inputOffset += dataWidth;
    }

    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    return bitmap;
  }

    public Bitmap renderCroppedBitmap() {
        System.out.println("console:!!!!!activity.yuvData="+yuvData.length);
        System.out.println("console:!!!!!activity.top="+top);
        System.out.println("console:!!!!!activity.left="+left);
        System.out.println("console:!!!!!activity.dataWidth="+dataWidth);
        System.out.println("console:!!!!!activity.width="+getWidth());
        System.out.println("console:!!!!!activity.dataHeight="+dataHeight);
        System.out.println("console:!!!!!activity.height="+getHeight());

        System.out.println("console:!!!!!activity.inputOffset="+top * dataWidth + left);
        System.out.println("console:!!!!!activity.Y/UV="+(dataWidth*dataHeight)/(yuvData.length-(dataWidth*dataHeight)));
        int width = getWidth();
        int height = getHeight();
//        int[] pixels = new int[width * height];
        byte[] yuv = yuvData;
        int[] i = new int[dataWidth * dataHeight*3];
        decodeYUV420SPQuarterRes(i, yuv, dataWidth, dataHeight);
//        stripYUV(i, yuv,width,height);
        Bitmap mBitmap =  Bitmap.createBitmap(i, 0, dataWidth, dataWidth, dataHeight,
                Bitmap.Config.RGB_565);
        //Bitmap.createBitmap(i, dataWidth, dataHeight, Bitmap.Config.RGB_565);
//        mBitmap.setPixels(i, 0, width, 0, 0, width, height);
       /* pixels = decodeYUV420SPQuarterRes(pixels,yuv,width,height);
        Bitmap bitmap = Bitmap.createBitmap(width, height,
                Bitmap.Config.RGB_565);
        bitmap.setPixels(pixels,0,width,0,0,width,height);*/
        return mBitmap;
    }
    public void stripYUV(int[] rgb, byte[] yuv, int width, int
            height) throws NullPointerException, IllegalArgumentException {
        int Y;
        int inputOffset = top * dataWidth + left;
        for(int j = 0; j < height; j++) {
            int pixPtr = j * width;
            for(int i = 0; i < width; i++) {
                Y = yuv[inputOffset +i]; if(Y < 0) Y += 255;
                rgb[pixPtr+i] = 0xff000000 + Y*0x00010101;//(Y << 16) + (Y <<  + Y;
            }
            inputOffset += dataWidth;
        }
    }
     public void decodeYUV420SP(int[] rgb, byte[] yuv420sp, int width,
                                      int height) {
        int inputOffset = top * dataWidth + left;
        final int frameSize = width * height;
        for (int j = 0, yp = 0; j < height; j++) {
            int outputOffset = j * width;
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & ((int) yuv420sp[inputOffset + i])) - 16;
                if (y < 0)
                    y = 0;
//                if ((i & 1) == 0) {
                    int t=(inputOffset + i)/4;
                    int thisUvp = frameSize + t*2 ;
                    v = (0xff & yuv420sp[thisUvp]) - 128;
                    u = (0xff & yuv420sp[thisUvp+1]) - 128;
//                }
                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);
                if (r < 0)
                    r = 0;
                else if (r > 262143)
                    r = 262143;
                if (g < 0)
                    g = 0;
                else if (g > 262143)
                    g = 262143;
                if (b < 0)
                    b = 0;
                else if (b > 262143)
                    b = 262143;
                rgb[outputOffset + i] = 0xff000000 | ((r << 6) & 0xff0000)
                        | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
            inputOffset += dataWidth;
        }
    }
    public static void decodeYUV420SPrgb565(int[] rgb, byte[] yuv420sp, int width,
                                            int height) {
        final int frameSize = width * height;
        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & ((int) yuv420sp[yp])) - 16;
                if (y < 0)
                    y = 0;
                if ((i & 3) == 0) {
                    v = (0xff & yuv420sp[uvp++]) - 128;
                    u = (0xff & yuv420sp[uvp++]) - 128;
                }
                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);
                if (r < 0)
                    r = 0;
                else if (r > 262143)
                    r = 262143;
                if (g < 0)
                    g = 0;
                else if (g > 262143)
                    g = 262143;
                if (b < 0)
                    b = 0;
                else if (b > 262143)
                    b = 262143;
                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000)
                        | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
    }

    public static int[] decodeYUV420SPQuarterRes(int[] rgb, byte[] yuv420sp, int width, int height) {
        final int frameSize = width * height;

        for (int j = 0, ypd = 0; j < height; j += 4) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i += 4, ypd++) {
                int y = (0xff & (yuv420sp[j * width + i])) - 16;
                if (y < 0) {
                    y = 0;
                }
                if ((i & 1) == 0) {
                    v = (0xff & yuv420sp[uvp++]) - 128;
                    u = (0xff & yuv420sp[uvp++]) - 128;
                    uvp += 2;  // Skip the UV values for the 4 pixels skipped in between
                }
                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                if (r < 0) {
                    r = 0;
                } else if (r > 262143) {
                    r = 262143;
                }
                if (g < 0) {
                    g = 0;
                } else if (g > 262143) {
                    g = 262143;
                }
                if (b < 0) {
                    b = 0;
                } else if (b > 262143) {
                    b = 262143;
                }

                rgb[ypd] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) |
                        ((b >> 10) & 0xff);
            }
        }
        return rgb;
    }

    //YV12是yuv420格式，是3个plane，排列方式为(Y)(V)(U)
    /*public static int[] YV12ToRGB(byte[] src, int width, int height){
        int numOfPixel = width * height;
        int positionOfV = numOfPixel;
        int positionOfU = numOfPixel/4 + numOfPixel;
        int[] rgb = new int[numOfPixel*3];

        for(int i=0; i<height; i++){
            int startY = i*width;
            int step = (i/2)*(width/2);
            int startV = positionOfV + step;
            int startU = positionOfU + step;
            for(int j = 0; j < width; j++){
                int Y = startY + j;
                int V = startV + j/2;
                int U = startU + j/2;
                int index = Y*3;

                //rgb[index+R] = (int)((src[Y]&0xff) + 1.4075 * ((src[V]&0xff)-128));
                //rgb[index+G] = (int)((src[Y]&0xff) - 0.3455 * ((src[U]&0xff)-128) - 0.7169*((src[V]&0xff)-128));
                //rgb[index+B] = (int)((src[Y]&0xff) + 1.779 * ((src[U]&0xff)-128));
                RGB tmp = yuvTorgb(src[Y], src[U], src[V]);
                rgb[index+R] = tmp.r;
                rgb[index+G] = tmp.g;
                rgb[index+B] = tmp.b;
            }
        }
        return rgb;
    }*/

}
