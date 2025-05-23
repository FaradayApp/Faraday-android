/*
 * Copyright 2018-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import androidx.core.content.edit
import im.vector.app.core.di.DefaultPreferences
import im.vector.app.core.resources.BuildMeta
import im.vector.lib.strings.CommonStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.IllformedLocaleException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Object to manage the Locale choice of the user.
 */
@Singleton
class VectorLocale @Inject constructor(
        private val context: Context,
        private val buildMeta: BuildMeta,
        @DefaultPreferences
        private val preferences: SharedPreferences,
) {
    companion object {
        const val APPLICATION_LOCALE_COUNTRY_KEY = "APPLICATION_LOCALE_COUNTRY_KEY"
        const val APPLICATION_LOCALE_VARIANT_KEY = "APPLICATION_LOCALE_VARIANT_KEY"
        const val APPLICATION_LOCALE_LANGUAGE_KEY = "APPLICATION_LOCALE_LANGUAGE_KEY"
        private const val APPLICATION_LOCALE_SCRIPT_KEY = "APPLICATION_LOCALE_SCRIPT_KEY"
        private const val ISO_15924_LATN = "Latn"
    }

    private val defaultLocale = Locale("en", "US")

    /**
     * The cache of supported application languages.
     */
    private val supportedLocales = mutableListOf<Locale>()

    /**
     * Provides the current application locale.
     */
    var applicationLocale = defaultLocale
        private set
        get() {
            return if (followSystemLocale) {
                Locale.getDefault()
            } else {
                field
            }
        }

    /**
     * Whether to always follow the system locale
     */
    var followSystemLocale: Boolean = false

    /**
     * Init this singleton.
     */
    fun init() {
        followSystemLocale = preferences.getBoolean(VectorPreferences.SETTINGS_FOLLOW_SYSTEM_LOCALE, false)
        reloadLocale()
    }

    fun reloadLocale() {
        if (followSystemLocale) {
            // Locale.getDefault() may have been changed by us, so we need to restore it from the system configuration explicitly
            val systemLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Resources.getSystem().configuration.locales[0]
            } else {
                @Suppress("DEPRECATION") // Deprecated in API level 24, for which we use above ^
                Resources.getSystem().configuration.locale
            }
            if (systemLocale != Locale.getDefault()) {
                Locale.setDefault(systemLocale)
            }
            return
        }

        if (preferences.contains(APPLICATION_LOCALE_LANGUAGE_KEY)) {
            applicationLocale = Locale(
                    preferences.getString(APPLICATION_LOCALE_LANGUAGE_KEY, "")!!,
                    preferences.getString(APPLICATION_LOCALE_COUNTRY_KEY, "")!!,
                    preferences.getString(APPLICATION_LOCALE_VARIANT_KEY, "")!!
            )
        } else {
            applicationLocale = Locale.getDefault()

            // detect if the default language is used
            val defaultStringValue = getString(context, defaultLocale, CommonStrings.resources_country_code)
            if (defaultStringValue == getString(context, applicationLocale, CommonStrings.resources_country_code)) {
                applicationLocale = defaultLocale
            }

            saveApplicationLocale(applicationLocale)
        }
    }

    /**
     * Save the new application locale.
     */
    fun saveApplicationLocale(locale: Locale) {
        applicationLocale = locale
        followSystemLocale = false

        preferences.edit {
            val language = locale.language
            if (language.isEmpty()) {
                remove(APPLICATION_LOCALE_LANGUAGE_KEY)
            } else {
                putString(APPLICATION_LOCALE_LANGUAGE_KEY, language)
            }

            val country = locale.country
            if (country.isEmpty()) {
                remove(APPLICATION_LOCALE_COUNTRY_KEY)
            } else {
                putString(APPLICATION_LOCALE_COUNTRY_KEY, country)
            }

            val variant = locale.variant
            if (variant.isEmpty()) {
                remove(APPLICATION_LOCALE_VARIANT_KEY)
            } else {
                putString(APPLICATION_LOCALE_VARIANT_KEY, variant)
            }

            val script = locale.script
            if (script.isEmpty()) {
                remove(APPLICATION_LOCALE_SCRIPT_KEY)
            } else {
                putString(APPLICATION_LOCALE_SCRIPT_KEY, script)
            }
        }
    }

    /**
     * Get String from a locale.
     *
     * @param context the context
     * @param locale the locale
     * @param resourceId the string resource id
     * @return the localized string
     */
    private fun getString(context: Context, locale: Locale, resourceId: Int): String {
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return try {
            context.createConfigurationContext(config).getText(resourceId).toString()
        } catch (e: Exception) {
            Timber.e(e, "## getString() failed")
            // use the default one
            context.getString(resourceId)
        }
    }

    /**
     * Init the supported application locales list.
     */
    private fun initApplicationLocales() {
        val knownLocalesSet = HashSet<Triple<String, String, String>>()

        try {
            val availableLocales = Locale.getAvailableLocales()

            for (locale in availableLocales) {
                knownLocalesSet.add(
                        Triple(
                                getString(context, locale, CommonStrings.resources_language),
                                getString(context, locale, CommonStrings.resources_country_code),
                                getString(context, locale, CommonStrings.resources_script)
                        )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "## getApplicationLocales() : failed")
            knownLocalesSet.add(
                    Triple(
                            context.getString(CommonStrings.resources_language),
                            context.getString(CommonStrings.resources_country_code),
                            context.getString(CommonStrings.resources_script)
                    )
            )
        }

        val list = knownLocalesSet.mapNotNull { (language, country, script) ->
            try {
                Locale.Builder()
                        .setLanguage(language)
                        .setRegion(country)
                        .setScript(script)
                        .build()
            } catch (exception: IllformedLocaleException) {
                if (buildMeta.isDebug) {
                    //throw exception
                    exception.printStackTrace()
                }
                // Ignore this locale in production
                null
            }
        }
                // sort by human display names
                .sortedBy { localeToLocalisedString(it).lowercase(it) }

        supportedLocales.clear()
        supportedLocales.addAll(list)
    }

    /**
     * Convert a locale to a string.
     *
     * @param locale the locale to convert
     * @return the string
     */
    fun localeToLocalisedString(locale: Locale): String {
        return buildString {
            append(locale.getDisplayLanguage(locale))

            if (locale.script != ISO_15924_LATN && locale.getDisplayScript(locale).isNotEmpty()) {
                append(" - ")
                append(locale.getDisplayScript(locale))
            }

            if (locale.getDisplayCountry(locale).isNotEmpty()) {
                append(" (")
                append(locale.getDisplayCountry(locale))
                append(")")
            }
        }
    }

    /**
     * Information about the locale in the current locale.
     *
     * @param locale the locale to get info from
     * @return the string
     */
    fun localeToLocalisedStringInfo(locale: Locale): String {
        return buildString {
            append("[")
            append(locale.displayLanguage)
            if (locale.script != ISO_15924_LATN) {
                append(" - ")
                append(locale.displayScript)
            }
            if (locale.displayCountry.isNotEmpty()) {
                append(" (")
                append(locale.displayCountry)
                append(")")
            }
            append("]")
        }
    }

    suspend fun getSupportedLocales(): List<Locale> {
        if (supportedLocales.isEmpty()) {
            // init the known locales in background
            withContext(Dispatchers.IO) {
                initApplicationLocales()
            }
        }
        return supportedLocales
    }
}
