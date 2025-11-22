package org.xbmc.kodi.content;

import org.xbmc.kodi.R;

import androidx.core.content.FileProvider;

public class XBMCFileProvider extends FileProvider
{
  public XBMCFileProvider()
  {
    super(R.xml.file_paths);
  }
}
