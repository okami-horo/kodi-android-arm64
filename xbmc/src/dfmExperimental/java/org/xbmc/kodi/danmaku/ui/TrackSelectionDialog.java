package org.xbmc.kodi.danmaku.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.xbmc.kodi.R;
import org.xbmc.kodi.danmaku.DanmakuEngine;
import org.xbmc.kodi.danmaku.api.DanmakuApi;
import org.xbmc.kodi.danmaku.model.MediaKey;
import org.xbmc.kodi.danmaku.model.TrackCandidate;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight dialog that lists discovered danmaku tracks and allows manual fallback.
 * Host (Activity/Fragment) is responsible for wiring the manual pick intent.
 */
public class TrackSelectionDialog {
    public interface Listener {
        void onManualPickRequested();
    }

    private final Context context;
    private final DanmakuApi api;
    private final Listener listener;

    public TrackSelectionDialog(Context context, DanmakuApi api, Listener listener) {
        this.context = context;
        this.api = api;
        this.listener = listener;
    }

    public void show(MediaKey mediaKey) {
        List<TrackCandidate> candidates = api.listTracks(mediaKey);
        if (candidates.isEmpty()) {
            showEmptyDialog();
            return;
        }
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_danmaku_tracks, null);
        ListView listView = view.findViewById(R.id.list_tracks);
        TextView subtitle = view.findViewById(R.id.subtitle);
        subtitle.setText(R.string.danmaku_tracks_hint);

        List<String> labels = new ArrayList<>();
        for (TrackCandidate candidate : candidates) {
            labels.add(formatLabel(candidate));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, labels);
        listView.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.danmaku_tracks_title)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        listView.setOnItemClickListener((parent, v, position, id) -> {
            TrackCandidate candidate = candidates.get(position);
            api.selectTrack(mediaKey, candidate.getTrack().getId());
            dialog.dismiss();
        });

        dialog.show();
    }

    public void showLoadError(@Nullable DanmakuEngine.LoadError error) {
        if (error == null) {
            return;
        }
        int messageRes = R.string.danmaku_tracks_error_generic;
        if ("missing".equals(error.getReason())) {
            messageRes = R.string.danmaku_tracks_error_missing;
        } else if ("MALFORMED".equals(error.getReason())) {
            messageRes = R.string.danmaku_tracks_error_malformed;
        } else if ("IO".equals(error.getReason())) {
            messageRes = R.string.danmaku_tracks_error_io;
        }
        new AlertDialog.Builder(context)
                .setTitle(R.string.danmaku_tracks_error_title)
                .setMessage(messageRes)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showEmptyDialog() {
        new AlertDialog.Builder(context)
                .setTitle(R.string.danmaku_tracks_empty_title)
                .setMessage(R.string.danmaku_tracks_empty_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.danmaku_tracks_action_manual, (d, which) -> {
                    if (listener != null) {
                        listener.onManualPickRequested();
                    }
                })
                .show();
    }

    private String formatLabel(TrackCandidate candidate) {
        String base = candidate.getTrack().getName();
        String reason = candidate.getReason();
        if (reason == null || reason.isEmpty()) {
            return base;
        }
        return base + " • " + reason;
    }
}
