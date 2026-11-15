package com.sapotlokal.util

import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.sapotlokal.R
import java.util.Locale

/**
 * The app's language: English ("en") or Bahasa Melayu ("ms").
 *
 * Uses Android's per-app language support (AppCompatDelegate), so the choice is remembered, shows in
 * the system's per-app language settings on Android 13+, and switching restarts the visible screen
 * in the new language straight away. With nothing chosen yet, a phone set to Malay starts in Malay;
 * everything else starts in English.
 */
object AppLanguage {
    const val EN = "en"
    const val MS = "ms"

    fun current(): String {
        val chosen = AppCompatDelegate.getApplicationLocales()
        val tag = if (!chosen.isEmpty) chosen[0]?.language else Locale.getDefault().language
        return if (tag == MS || tag == "in") MS else EN
    }

    fun set(code: String) {
        if (code == current()) return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
    }

    /** Wires the EN | BM switch (layout_language_toggle) found inside [root]. */
    fun bindToggle(root: View) {
        val en = root.findViewById<TextView>(R.id.langEn) ?: return
        val ms = root.findViewById<TextView>(R.id.langMs) ?: return
        fun style() {
            val isMs = current() == MS
            fun apply(tv: TextView, selected: Boolean) {
                tv.setBackgroundResource(if (selected) R.drawable.bg_lang_selected else 0)
                tv.setTextColor(root.context.getColor(if (selected) android.R.color.white else R.color.hu_soft))
            }
            apply(en, !isMs)
            apply(ms, isMs)
        }
        en.setOnClickListener { set(EN) }
        ms.setOnClickListener { set(MS) }
        style()
    }
}
