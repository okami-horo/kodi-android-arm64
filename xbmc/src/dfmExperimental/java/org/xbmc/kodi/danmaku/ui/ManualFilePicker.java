package org.xbmc.kodi.danmaku.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import org.xbmc.kodi.danmaku.DanmakuEngine;
import org.xbmc.kodi.danmaku.model.MediaKey;

import java.io.File;
import java.util.Locale;

/**
 * Helper to build a document picker intent and validate selections for danmaku XML files.
 * UI wiring is expected to live in the hosting Activity/Fragment; this class keeps logic testable.
 */
public class ManualFilePicker {
    private static final String MIME_XML = "text/xml";

    public Intent buildOpenDocumentIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(MIME_XML);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, (Uri) null);
        }
        return intent;
    }

    /**
     * Validates a file path selection and instructs the engine to load it.
     *
     * @return true if the selection is accepted and handed to the engine.
     */
    public boolean selectLocalFile(String path, MediaKey mediaKey, DanmakuEngine engine) {
        if (engine == null || mediaKey == null || path == null || path.isEmpty()) {
            return false;
        }
        File file = new File(path);
        if (!file.exists() || !file.canRead()) {
            return false;
        }
        if (!path.toLowerCase(Locale.US).endsWith(".xml")) {
            return false;
        }
        engine.selectTrack(mediaKey, file.getAbsolutePath());
        return true;
    }
}
