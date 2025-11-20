package org.xbmc.kodi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

public class XBMCBroadcastReceiver extends BroadcastReceiver
{
  native void _onReceive(Intent intent);

  private static final String TAG = "Kodi";

  @Override
  public void onReceive(Context context, Intent intent)
  {
    if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))
    {
      if (XBMCProperties.getBoolProperty("xbmc.autostart"))
      {
        // Run Kodi
        Intent i = new Intent();
        PackageManager manager = context.getPackageManager();
        i = manager.getLaunchIntentForPackage("org.xbmc.kodi");
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        context.startActivity(i);
      }
    }
    else
    {
      try
      {
        _onReceive(intent);
      }
      catch (UnsatisfiedLinkError e)
      {
        Log.e(TAG, "XBMCBroadcastReceiver: Native not registered");
      }
    }
  }
}
