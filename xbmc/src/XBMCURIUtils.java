package org.xbmc.kodi;

import android.util.Log;

public class XBMCURIUtils
{
  native String _substitutePath(String path);

  private static String TAG = "Kodi";

  public XBMCURIUtils()
  {
  }

  public String substitutePath(String path)
  {
    try
    {
      return _substitutePath(path);
    }
    catch (Exception e)
    {
      e.printStackTrace();
      Log.e(TAG, "XBMCURIUtils.substitutePath: Exception");
      return null;
    }
  }

}
