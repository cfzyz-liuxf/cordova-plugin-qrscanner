package org.apache.cordova.qrscanner;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import com.liuxf.qrscanner.CaptureActivity;
import com.google.zxing.client.android.Intents;
import com.google.zxing.client.android.encode.EncodeActivity;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class QrScanner extends CordovaPlugin {

  public static final int REQUEST_CODE = 0x0ba7c0de;

  private static final String SCAN = "scan";
  private static final String ENCODE = "encode";
  private static final String CANCELLED = "cancelled";
  private static final String FORMAT = "format";
  private static final String TEXT = "text";
  private static final String DATA = "data";
  private static final String TYPE = "type";
  private static final String RESULTDISPLAY_DURATION = "resultDisplayDuration";
  private static final String SAVE_HISTORY = "saveHistory";
  private static final String FORMATS = "formats";
  private static final String PROMPT = "prompt";
  private static final String TEXT_TYPE = "TEXT_TYPE";

  private static final String TAG = "QrScanner";

  private String[] permissions = {Manifest.permission.CAMERA};

  private JSONArray requestArgs;
  private CallbackContext callbackContext;

  @Override
  public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
    this.callbackContext = callbackContext;
    this.requestArgs = args;

    if (action.equals(ENCODE)) {
      JSONObject obj = args.optJSONObject(0);
      if (obj != null) {
        String type = obj.optString(TYPE);
        String data = obj.optString(DATA);
        if (type == null)
          type = TEXT_TYPE;
        if (data == null) {
          callbackContext.error("User did not specify data to encode");
          return true;
        }
        encode(type, data);
      } else {
        callbackContext.error("User did not specify data to encode");
        return true;
      }
    } else if (action.equals(SCAN)) {
      if (!hasPermisssion()) {
        requestPermissions(0);
      } else
        scan(args);
    } else
      return false;
    return true;
  }

  /**
   * Starts an intent to scan and decode.
   */
  public void scan(final JSONArray args) {
    final CordovaPlugin that = this;
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        Intent intentScan = new Intent(that.cordova.getActivity().getBaseContext(), CaptureActivity.class);
        intentScan.setAction(Intents.Scan.ACTION);
        intentScan.addCategory(Intent.CATEGORY_DEFAULT);
        // add config as intent extras
        if (args.length() > 0) {
          JSONObject obj;
          JSONArray names;
          String key;
          Object value;
          for (int i = 0; i < args.length(); i++) {
            try {
              obj = args.getJSONObject(i);
            } catch (JSONException e) {
              Log.i(TAG, e.getLocalizedMessage());
              continue;
            }
            names = obj.names();
            for (int j = 0; j < names.length(); j++) {
              try {
                key = names.getString(j);
                value = obj.get(key);
                if (value instanceof Integer) {
                  intentScan.putExtra(key, (Integer) value);
                } else if (value instanceof String) {
                  intentScan.putExtra(key, (String) value);
                }
              } catch (JSONException e) {
                Log.i(TAG, e.getLocalizedMessage());
              }
            }
            intentScan.putExtra(Intents.Scan.SAVE_HISTORY, obj.optBoolean(SAVE_HISTORY, false));
            if (obj.has(RESULTDISPLAY_DURATION)) {
              intentScan.putExtra(Intents.Scan.RESULT_DISPLAY_DURATION_MS, "" + obj.optLong(RESULTDISPLAY_DURATION));
            }
            if (obj.has(FORMATS)) {
              intentScan.putExtra(Intents.Scan.FORMATS, obj.optString(FORMATS));
            }
            if (obj.has(PROMPT)) {
              intentScan.putExtra(Intents.Scan.PROMPT_MESSAGE, obj.optString(PROMPT));
            }
          }
        }
        // avoid calling other phonegap apps
        intentScan.setPackage(that.cordova.getActivity().getApplicationContext().getPackageName());
        that.cordova.startActivityForResult(that, intentScan, REQUEST_CODE);
      }
    });
  }

  /**
   * Called when the barcode scanner intent completes.
   *
   * @param requestCode The request code originally supplied to startActivityForResult(),
   *                    allowing you to identify who this result came from.
   * @param resultCode  The integer result code returned by the child activity through its setResult().
   * @param intent      An Intent, which can return result data to the caller (various data can be attached to Intent "extras").
   */
  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    if (requestCode == REQUEST_CODE && this.callbackContext != null) {
      if (resultCode == Activity.RESULT_OK) {
        JSONObject obj = new JSONObject();
        try {
          obj.put(TEXT, intent.getStringExtra("SCAN_RESULT"));
          obj.put(FORMAT, intent.getStringExtra("SCAN_RESULT_FORMAT"));
          obj.put(CANCELLED, false);
        } catch (JSONException e) {
          Log.d(TAG, "This should never happen");
        }
        //this.success(new PluginResult(PluginResult.Status.OK, obj), this.callback);
        this.callbackContext.success(obj);
      } else if (resultCode == Activity.RESULT_CANCELED) {
        JSONObject obj = new JSONObject();
        try {
          obj.put(TEXT, "");
          obj.put(FORMAT, "");
          obj.put(CANCELLED, true);
        } catch (JSONException e) {
          Log.d(TAG, "This should never happen");
        }
        //this.success(new PluginResult(PluginResult.Status.OK, obj), this.callback);
        this.callbackContext.success(obj);
      } else {
        //this.error(new PluginResult(PluginResult.Status.ERROR), this.callback);
        this.callbackContext.error("Unexpected error");
      }
    }
  }

  /**
   * Initiates a barcode encode.
   *
   * @param type Endoiding type.
   * @param data The data to encode in the bar code.
   */
  public void encode(String type, String data) {
    Intent intentEncode = new Intent(this.cordova.getActivity().getBaseContext(), EncodeActivity.class);
    intentEncode.setAction(Intents.Encode.ACTION);
    intentEncode.putExtra(Intents.Encode.TYPE, type);
    intentEncode.putExtra(Intents.Encode.DATA, data);
    // avoid calling other phonegap apps
    intentEncode.setPackage(this.cordova.getActivity().getApplicationContext().getPackageName());

    this.cordova.getActivity().startActivity(intentEncode);
  }

  /**
   * check application's permissions
   */
  public boolean hasPermisssion() {
    for (String p : permissions) {
      if (!PermissionHelper.hasPermission(this, p)) {
        return false;
      }
    }
    return true;
  }

  /**
   * We override this so that we can access the permissions variable, which no longer exists in
   * the parent class, since we can't initialize it reliably in the constructor!
   *
   * @param requestCode The code to get request action
   */
  public void requestPermissions(int requestCode) {
    PermissionHelper.requestPermissions(this, requestCode, permissions);
  }

  /**
   * processes the result of permission request
   *
   * @param requestCode  The code to get request action
   * @param permissions  The collection of permissions
   * @param grantResults The result of grant
   */
  public void onRequestPermissionResult(int requestCode, String[] permissions,
                                        int[] grantResults) throws JSONException {
    PluginResult result;
    for (int r : grantResults) {
      if (r == PackageManager.PERMISSION_DENIED) {
        Log.d(TAG, "Permission Denied!");
        result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION);
        this.callbackContext.sendPluginResult(result);
        return;
      }
    }

    switch (requestCode) {
      case 0:
        scan(this.requestArgs);
        break;
    }
  }

  /**
   * This plugin launches an external Activity when the camera is opened, so we
   * need to implement the save/restore API in case the Activity gets killed
   * by the OS while it's in the background.
   */
  public void onRestoreStateForActivityResult(Bundle state, CallbackContext callbackContext) {
    this.callbackContext = callbackContext;
  }

}
