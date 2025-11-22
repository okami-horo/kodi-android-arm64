package org.xbmc.kodi.danmaku.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import org.xbmc.kodi.danmaku.DanmakuEngine;
import org.xbmc.kodi.danmaku.model.MediaKey;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
        intent.setFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
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

    public boolean selectDocument(Context context, Uri uri, MediaKey mediaKey, DanmakuEngine engine) {
        if (context == null || uri == null) {
            return false;
        }
        String name = queryDisplayName(context.getContentResolver(), uri);
        if (name == null || !name.toLowerCase(Locale.US).endsWith(".xml")) {
            name = "danmaku-" + System.currentTimeMillis() + ".xml";
        }
        File cacheDir = new File(context.getFilesDir(), "danmaku/manual");
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            return false;
        }
        File target = new File(cacheDir, name);
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(target)) {
            if (in == null) {
                return false;
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (IOException ex) {
            return false;
        }
        return selectLocalFile(target.getAbsolutePath(), mediaKey, engine);
    }

    private String queryDisplayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        }
        return null;
    }
}
