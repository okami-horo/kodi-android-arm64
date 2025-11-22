package org.xbmc.kodi.danmaku.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;

import org.xbmc.kodi.R;

/**
 * Hosts the PreferenceScreen backed by settings_danmaku.xml.
 */
public class DanmakuSettingsActivity extends AppCompatActivity {

    public static void launch(Context context) {
        Intent intent = new Intent(context, DanmakuSettingsActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new DanmakuSettingsFragment())
                .commit();
    }

    public static class DanmakuSettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            getPreferenceManager().setSharedPreferencesName(DanmakuSettingsStore.PREFS_NAME);
            setPreferencesFromResource(R.xml.settings_danmaku, rootKey);
            DanmakuSettingsStore.ensureDefaults(getPreferenceManager().getSharedPreferences());
            bindPreferences();
        }

        private void bindPreferences() {
            bindSeekBar("danmaku_text_scale");
            bindSeekBar("danmaku_speed");
            bindSeekBar("danmaku_alpha");
            bindSeekBar("danmaku_max_on_screen");
            bindSeekBar("danmaku_offset");
            EditTextPreference keywords = findPreference("danmaku_keyword_filter");
            if (keywords != null) {
                keywords.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
            }
            Preference readability = findPreference("danmaku_readability_hint");
            if (readability != null) {
                readability.setSelectable(false);
            }
        }

        private void bindSeekBar(String key) {
            SeekBarPreference preference = findPreference(key);
            if (preference != null) {
                preference.setShowSeekBarValue(true);
            }
        }
    }
}
