/*
 * Copyright 2018 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package im.vector.app.features.settings

import android.content.Context
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.BoolRes
import androidx.core.content.edit
import androidx.core.net.toUri
import com.squareup.seismic.ShakeDetector
import de.spiritcroc.matrixsdk.StaticScSdkHelper
import im.vector.app.core.di.DefaultPreferences
import im.vector.app.core.resources.BuildMeta
import im.vector.app.core.resources.StringProvider
import im.vector.app.features.VectorFeatures
import im.vector.app.features.home.ShortcutsHandler
import im.vector.app.features.home.room.detail.timeline.helper.MatrixItemColorProvider
import im.vector.app.features.homeserver.ServerUrlsRepository
import im.vector.app.features.themes.BubbleThemeUtils
import im.vector.app.features.themes.ThemeUtils
import im.vector.lib.strings.CommonStrings
import org.matrix.android.sdk.api.extensions.tryOrNull
import org.matrix.android.sdk.api.session.room.model.RoomSummary
import org.matrix.android.sdk.internal.settings.DefaultLightweightSettingsStorage
import timber.log.Timber
import javax.inject.Inject

class VectorPreferences @Inject constructor(
        private val context: Context,
        private val buildMeta: BuildMeta,
        private val vectorFeatures: VectorFeatures,
        @DefaultPreferences
        private val defaultPrefs: SharedPreferences,
        private val stringProvider: StringProvider,
) : StaticScSdkHelper.ScSdkPreferenceProvider {

    private val bubbleThemeUtils: BubbleThemeUtils = BubbleThemeUtils(context)

    companion object {
        const val SETTINGS_HELP_PREFERENCE_KEY = "SETTINGS_HELP_PREFERENCE_KEY"
        const val SETTINGS_CHANGE_PASSWORD_PREFERENCE_KEY = "SETTINGS_CHANGE_PASSWORD_PREFERENCE_KEY"
        const val SETTINGS_VERSION_PREFERENCE_KEY = "SETTINGS_VERSION_PREFERENCE_KEY"
        const val SETTINGS_SDK_VERSION_PREFERENCE_KEY = "SETTINGS_SDK_VERSION_PREFERENCE_KEY"
        const val SETTINGS_CRYPTO_VERSION_PREFERENCE_KEY = "SETTINGS_CRYPTO_VERSION_PREFERENCE_KEY"
        const val SETTINGS_LOGGED_IN_PREFERENCE_KEY = "SETTINGS_LOGGED_IN_PREFERENCE_KEY"
        const val SETTINGS_HOME_SERVER_PREFERENCE_KEY = "SETTINGS_HOME_SERVER_PREFERENCE_KEY"
        const val SETTINGS_IDENTITY_SERVER_PREFERENCE_KEY = "SETTINGS_IDENTITY_SERVER_PREFERENCE_KEY"
        const val SETTINGS_DISCOVERY_PREFERENCE_KEY = "SETTINGS_DISCOVERY_PREFERENCE_KEY"
        const val SETTINGS_EMAILS_AND_PHONE_NUMBERS_PREFERENCE_KEY = "SETTINGS_EMAILS_AND_PHONE_NUMBERS_PREFERENCE_KEY"
        const val SETTINGS_EXTERNAL_ACCOUNT_MANAGEMENT_KEY = "SETTINGS_EXTERNAL_ACCOUNT_MANAGEMENT_KEY"

        const val SETTINGS_CLEAR_CACHE_PREFERENCE_KEY = "SETTINGS_CLEAR_CACHE_PREFERENCE_KEY"
        const val SETTINGS_INIT_SYNC_PREFERENCE_KEY = "SETTINGS_INIT_SYNC_PREFERENCE_KEY"
        const val SETTINGS_CLEAR_MEDIA_CACHE_PREFERENCE_KEY = "SETTINGS_CLEAR_MEDIA_CACHE_PREFERENCE_KEY"
        const val SETTINGS_USER_SETTINGS_PREFERENCE_KEY = "SETTINGS_USER_SETTINGS_PREFERENCE_KEY"
        const val SETTINGS_CONTACT_PREFERENCE_KEYS = "SETTINGS_CONTACT_PREFERENCE_KEYS"
        const val SETTINGS_FDROID_BACKGROUND_SYNC_MODE = "SETTINGS_FDROID_BACKGROUND_SYNC_MODE"
        const val SETTINGS_BACKGROUND_SYNC_PREFERENCE_KEY = "SETTINGS_BACKGROUND_SYNC_PREFERENCE_KEY"
        const val SETTINGS_LABS_NEW_APP_LAYOUT_KEY = "SETTINGS_LABS_NEW_APP_LAYOUT_KEY"
        const val SETTINGS_LABS_DEFERRED_DM_KEY = "SETTINGS_LABS_DEFERRED_DM_KEY"
        const val SETTINGS_LABS_RICH_TEXT_EDITOR_KEY = "SETTINGS_LABS_RICH_TEXT_EDITOR_KEY"
        const val SETTINGS_LABS_NEW_SESSION_MANAGER_KEY = "SETTINGS_LABS_NEW_SESSION_MANAGER_KEY"
        const val SETTINGS_LABS_CLIENT_INFO_RECORDING_KEY = "SETTINGS_LABS_CLIENT_INFO_RECORDING_KEY"
        const val SETTINGS_LABS_VOICE_BROADCAST_KEY = "SETTINGS_LABS_VOICE_BROADCAST_KEY"
        const val SETTINGS_CRYPTOGRAPHY_PREFERENCE_KEY = "SETTINGS_CRYPTOGRAPHY_PREFERENCE_KEY"
        const val SETTINGS_PASSWORD_PREFERENCE_KEY = "SETTINGS_PASSWORD_PREFERENCE_KEY"
        const val SETTINGS_PASSWORD_CHANGE_PREFERENCE_KEY = "SETTINGS_PASSWORD_CHANGE_PREFERENCE_KEY"
        const val SETTINGS_NUKE_PASSWORD_PREFERENCE_KEY = "SETTINGS_NUKE_PASSWORD_PREFERENCE_KEY"
        const val SETTINGS_CURRENT_PASSWORD_PREFERENCE_KEY = "SETTINGS_CURRENT_PASSWORD_PREFERENCE_KEY"
        const val SETTINGS_NEW_PASSWORD_PREFERENCE_KEY = "SETTINGS_NEW_PASSWORD_PREFERENCE_KEY"
        const val SETTINGS_REPEAT_PASSWORD_PREFERENCE_KEY = "SETTINGS_REPEAT_PASSWORD_PREFERENCE_KEY"
        private const val SETTINGS_ROOM_SETTINGS_LABS_END_TO_END_PREFERENCE_KEY = "SETTINGS_ROOM_SETTINGS_LABS_END_TO_END_PREFERENCE_KEY"
        const val SETTINGS_ENCRYPTION_CROSS_SIGNING_PREFERENCE_KEY = "SETTINGS_ENCRYPTION_CROSS_SIGNING_PREFERENCE_KEY"
        const val SETTINGS_ENCRYPTION_EXPORT_E2E_ROOM_KEYS_PREFERENCE_KEY = "SETTINGS_ENCRYPTION_EXPORT_E2E_ROOM_KEYS_PREFERENCE_KEY"
        const val SETTINGS_ENCRYPTION_IMPORT_E2E_ROOM_KEYS_PREFERENCE_KEY = "SETTINGS_ENCRYPTION_IMPORT_E2E_ROOM_KEYS_PREFERENCE_KEY"
        const val SETTINGS_ENCRYPTION_NEVER_SENT_TO_PREFERENCE_KEY = "SETTINGS_ENCRYPTION_NEVER_SENT_TO_PREFERENCE_KEY"
        const val SETTINGS_SHOW_DEVICES_LIST_PREFERENCE_KEY = "SETTINGS_SHOW_DEVICES_LIST_PREFERENCE_KEY"
        const val SETTINGS_SHOW_DEVICES_LIST_V2_PREFERENCE_KEY = "SETTINGS_SHOW_DEVICES_LIST_V2_PREFERENCE_KEY"
        const val SETTINGS_ALLOW_INTEGRATIONS_KEY = "SETTINGS_ALLOW_INTEGRATIONS_KEY"
        const val SETTINGS_INTEGRATION_MANAGER_UI_URL_KEY = "SETTINGS_INTEGRATION_MANAGER_UI_URL_KEY"
        const val SETTINGS_SECURE_MESSAGE_RECOVERY_PREFERENCE_KEY = "SETTINGS_SECURE_MESSAGE_RECOVERY_PREFERENCE_KEY"
        const val SETTINGS_PERSISTED_SPACE_BACKSTACK = "SETTINGS_PERSISTED_SPACE_BACKSTACK"
        const val SETTINGS_SECURITY_INCOGNITO_KEYBOARD_PREFERENCE_KEY = "SETTINGS_SECURITY_INCOGNITO_KEYBOARD_PREFERENCE_KEY"

        const val SETTINGS_CRYPTOGRAPHY_HS_ADMIN_DISABLED_E2E_DEFAULT = "SETTINGS_CRYPTOGRAPHY_HS_ADMIN_DISABLED_E2E_DEFAULT"
//        const val SETTINGS_SECURE_BACKUP_RESET_PREFERENCE_KEY = "SETTINGS_SECURE_BACKUP_RESET_PREFERENCE_KEY"

        // user
        const val SETTINGS_PROFILE_PICTURE_PREFERENCE_KEY = "SETTINGS_PROFILE_PICTURE_PREFERENCE_KEY"

        // contacts
        const val SETTINGS_CONTACTS_PHONEBOOK_COUNTRY_PREFERENCE_KEY = "SETTINGS_CONTACTS_PHONEBOOK_COUNTRY_PREFERENCE_KEY"

        // interface
        const val SETTINGS_INTERFACE_LANGUAGE_PREFERENCE_KEY = "SETTINGS_INTERFACE_LANGUAGE_PREFERENCE_KEY"
        const val SETTINGS_INTERFACE_TEXT_SIZE_KEY = "SETTINGS_INTERFACE_TEXT_SIZE_KEY"
        const val SETTINGS_SHOW_URL_PREVIEW_KEY = "SETTINGS_SHOW_URL_PREVIEW_KEY"
        private const val SETTINGS_SEND_TYPING_NOTIF_KEY = "SETTINGS_SEND_TYPING_NOTIF_KEY"
        private const val SETTINGS_ENABLE_MARKDOWN_KEY = "SETTINGS_ENABLE_MARKDOWN_KEY"
        private const val SETTINGS_ENABLE_RICH_TEXT_FORMATTING_KEY = "SETTINGS_ENABLE_RICH_TEXT_FORMATTING_KEY"
        const val SETTINGS_ALWAYS_SHOW_TIMESTAMPS_KEY = "SETTINGS_ALWAYS_SHOW_TIMESTAMPS_KEY"
        private const val SETTINGS_12_24_TIMESTAMPS_KEY = "SETTINGS_12_24_TIMESTAMPS_KEY"
        private const val SETTINGS_SHOW_READ_RECEIPTS_KEY = "SETTINGS_SHOW_READ_RECEIPTS_KEY"
        private const val SETTINGS_SHOW_REDACTED_KEY = "SETTINGS_SHOW_REDACTED_KEY"
        private const val SETTINGS_SHOW_ROOM_MEMBER_STATE_EVENTS_KEY = "SETTINGS_SHOW_ROOM_MEMBER_STATE_EVENTS_KEY"
        private const val SETTINGS_SHOW_JOIN_LEAVE_MESSAGES_KEY = "SETTINGS_SHOW_JOIN_LEAVE_MESSAGES_KEY"
        private const val SETTINGS_SHOW_AVATAR_DISPLAY_NAME_CHANGES_MESSAGES_KEY = "SETTINGS_SHOW_AVATAR_DISPLAY_NAME_CHANGES_MESSAGES_KEY"
        private const val SETTINGS_SEND_MESSAGE_WITH_ENTER = "SETTINGS_SEND_MESSAGE_WITH_ENTER"
        private const val SETTINGS_ENABLE_CHAT_EFFECTS = "SETTINGS_ENABLE_CHAT_EFFECTS"
        private const val SETTINGS_SHOW_EMOJI_KEYBOARD = "SETTINGS_SHOW_EMOJI_KEYBOARD"
        private const val SETTINGS_LABS_ENABLE_LATEX_MATHS = "SETTINGS_LABS_ENABLE_LATEX_MATHS"
        const val SETTINGS_PRESENCE_USER_ALWAYS_APPEARS_OFFLINE = "SETTINGS_PRESENCE_USER_ALWAYS_APPEARS_OFFLINE"
        const val SETTINGS_AUTOPLAY_ANIMATED_IMAGES = "SETTINGS_AUTOPLAY_ANIMATED_IMAGES"
        private const val SETTINGS_ENABLE_DIRECT_SHARE = "SETTINGS_ENABLE_DIRECT_SHARE"

        // Room directory
        private const val SETTINGS_ROOM_DIRECTORY_SHOW_ALL_PUBLIC_ROOMS = "SETTINGS_ROOM_DIRECTORY_SHOW_ALL_PUBLIC_ROOMS"

        // Help
        private const val SETTINGS_SHOULD_SHOW_HELP_ON_ROOM_LIST_KEY = "SETTINGS_SHOULD_SHOW_HELP_ON_ROOM_LIST_KEY"

        // home
        private const val SETTINGS_PIN_UNREAD_MESSAGES_PREFERENCE_KEY = "SETTINGS_PIN_UNREAD_MESSAGES_PREFERENCE_KEY"
        private const val SETTINGS_PIN_MISSED_NOTIFICATIONS_PREFERENCE_KEY = "SETTINGS_PIN_MISSED_NOTIFICATIONS_PREFERENCE_KEY"

        // notifications
        const val SETTINGS_NUKE_PASSWORD_NOTIFICATIONS_PREFERENCE_KEY = "SETTINGS_NUKE_PASSWORD_NOTIFICATIONS_PREFERENCE_KEY"
        const val SETTINGS_ENABLE_ALL_NOTIF_PREFERENCE_KEY = "SETTINGS_ENABLE_ALL_NOTIF_PREFERENCE_KEY"
        const val SETTINGS_ENABLE_THIS_DEVICE_PREFERENCE_KEY = "SETTINGS_ENABLE_THIS_DEVICE_PREFERENCE_KEY"
        const val SETTINGS_EMAIL_NOTIFICATION_CATEGORY_PREFERENCE_KEY = "SETTINGS_EMAIL_NOTIFICATION_CATEGORY_PREFERENCE_KEY"

        //    public static final String SETTINGS_TURN_SCREEN_ON_PREFERENCE_KEY = "SETTINGS_TURN_SCREEN_ON_PREFERENCE_KEY";
        const val SETTINGS_SYSTEM_CALL_NOTIFICATION_PREFERENCE_KEY = "SETTINGS_SYSTEM_CALL_NOTIFICATION_PREFERENCE_KEY"
        const val SETTINGS_SYSTEM_NOISY_NOTIFICATION_PREFERENCE_KEY = "SETTINGS_SYSTEM_NOISY_NOTIFICATION_PREFERENCE_KEY"
        const val SETTINGS_SYSTEM_SILENT_NOTIFICATION_PREFERENCE_KEY = "SETTINGS_SYSTEM_SILENT_NOTIFICATION_PREFERENCE_KEY"
        const val SETTINGS_NOTIFICATION_RINGTONE_PREFERENCE_KEY = "SETTINGS_NOTIFICATION_RINGTONE_PREFERENCE_KEY"
        const val SETTINGS_NOTIFICATION_RINGTONE_SELECTION_PREFERENCE_KEY = "SETTINGS_NOTIFICATION_RINGTONE_SELECTION_PREFERENCE_KEY"

        // media
        private const val SETTINGS_DEFAULT_MEDIA_COMPRESSION_KEY = "SETTINGS_DEFAULT_MEDIA_COMPRESSION_KEY"
        private const val SETTINGS_DEFAULT_MEDIA_SOURCE_KEY = "SETTINGS_DEFAULT_MEDIA_SOURCE_KEY"
        private const val SETTINGS_PREVIEW_MEDIA_BEFORE_SENDING_KEY = "SETTINGS_PREVIEW_MEDIA_BEFORE_SENDING_KEY"
        private const val SETTINGS_PLAY_SHUTTER_SOUND_KEY = "SETTINGS_PLAY_SHUTTER_SOUND_KEY"

        // background sync
        const val SETTINGS_START_ON_BOOT_PREFERENCE_KEY = "SETTINGS_START_ON_BOOT_PREFERENCE_KEY"
        private const val SETTINGS_ENABLE_BACKGROUND_SYNC_PREFERENCE_KEY = "SETTINGS_ENABLE_BACKGROUND_SYNC_PREFERENCE_KEY"
        const val SETTINGS_SET_SYNC_TIMEOUT_PREFERENCE_KEY = "SETTINGS_SET_SYNC_TIMEOUT_PREFERENCE_KEY"
        const val SETTINGS_SET_SYNC_DELAY_PREFERENCE_KEY = "SETTINGS_SET_SYNC_DELAY_PREFERENCE_KEY"

        // notification method
        const val SETTINGS_NOTIFICATION_METHOD_KEY = "SETTINGS_NOTIFICATION_METHOD_KEY"

        // Calls
        const val SETTINGS_CALL_PREVENT_ACCIDENTAL_CALL_KEY = "SETTINGS_CALL_PREVENT_ACCIDENTAL_CALL_KEY"
        const val SETTINGS_CALL_USE_FALLBACK_CALL_ASSIST_SERVER_KEY = "SETTINGS_CALL_USE_FALLBACK_CALL_ASSIST_SERVER_KEY"
        const val SETTINGS_CALL_RINGTONE_USE_RIOT_PREFERENCE_KEY = "SETTINGS_CALL_RINGTONE_USE_RIOT_PREFERENCE_KEY"
        const val SETTINGS_CALL_RINGTONE_URI_PREFERENCE_KEY = "SETTINGS_CALL_RINGTONE_URI_PREFERENCE_KEY"

        private const val SETTINGS_DATA_SAVE_MODE_PREFERENCE_KEY = "SETTINGS_DATA_SAVE_MODE_PREFERENCE_KEY"
        private const val SETTINGS_USE_JITSI_CONF_PREFERENCE_KEY = "SETTINGS_USE_JITSI_CONF_PREFERENCE_KEY"

        const val SETTINGS_LABS_ALLOW_EXTENDED_LOGS = "SETTINGS_LABS_ALLOW_EXTENDED_LOGS"
        const val SETTINGS_LABS_AUTO_REPORT_UISI = "SETTINGS_LABS_AUTO_REPORT_UISI"
        const val SETTINGS_PREF_SPACE_SHOW_ALL_ROOM_IN_HOME = "SETTINGS_PREF_SPACE_SHOW_ALL_ROOM_IN_HOME"

        private const val SETTINGS_DEVELOPER_MODE_PREFERENCE_KEY = "SETTINGS_DEVELOPER_MODE_PREFERENCE_KEY"
        const val SETTINGS_DEVELOPER_MODE_KEY_REQUEST_AUDIT_KEY = "SETTINGS_DEVELOPER_MODE_KEY_REQUEST_AUDIT_KEY"
        private const val SETTINGS_LABS_SHOW_HIDDEN_EVENTS_PREFERENCE_KEY = "SETTINGS_LABS_SHOW_HIDDEN_EVENTS_PREFERENCE_KEY"
        private const val SETTINGS_LABS_ENABLE_SWIPE_TO_REPLY = "SETTINGS_LABS_ENABLE_SWIPE_TO_REPLY"
        private const val SETTINGS_DEVELOPER_MODE_FAIL_FAST_PREFERENCE_KEY = "SETTINGS_DEVELOPER_MODE_FAIL_FAST_PREFERENCE_KEY"
        private const val SETTINGS_DEVELOPER_MODE_SHOW_INFO_ON_SCREEN_KEY = "SETTINGS_DEVELOPER_MODE_SHOW_INFO_ON_SCREEN_KEY"
        private const val SETTINGS_ENABLE_MEMORY_LEAK_ANALYSIS_KEY = "SETTINGS_ENABLE_MEMORY_LEAK_ANALYSIS_KEY"

        const val SETTINGS_LABS_MSC3061_SHARE_KEYS_HISTORY = "SETTINGS_LABS_MSC3061_SHARE_KEYS_HISTORY"

        // SETTINGS_LABS_HIDE_TECHNICAL_E2E_ERRORS
        private const val SETTINGS_LABS_SHOW_COMPLETE_HISTORY_IN_ENCRYPTED_ROOM = "SETTINGS_LABS_SHOW_COMPLETE_HISTORY_IN_ENCRYPTED_ROOM"
        const val SETTINGS_LABS_UNREAD_NOTIFICATIONS_AS_TAB = "SETTINGS_LABS_UNREAD_NOTIFICATIONS_AS_TAB"

        // Rageshake
        const val SETTINGS_USE_RAGE_SHAKE_KEY = "SETTINGS_USE_RAGE_SHAKE_KEY"
        const val SETTINGS_RAGE_SHAKE_DETECTION_THRESHOLD_KEY = "SETTINGS_RAGE_SHAKE_DETECTION_THRESHOLD_KEY"

        // Security
        const val SETTINGS_SECURITY_USE_FLAG_SECURE = "SETTINGS_SECURITY_USE_FLAG_SECURE"
        const val SETTINGS_USER_AGENT_KEY = "SETTINGS_USER_AGENT_KEY"
        const val SETTINGS_SECURITY_USE_PIN_CODE_FLAG = "SETTINGS_SECURITY_USE_PIN_CODE_FLAG"
        const val SETTINGS_SECURITY_CHANGE_PIN_CODE_FLAG = "SETTINGS_SECURITY_CHANGE_PIN_CODE_FLAG"
        const val SETTINGS_SECURITY_USE_BIOMETRICS_FLAG = "SETTINGS_SECURITY_USE_BIOMETRICS_FLAG"
        private const val SETTINGS_SECURITY_USE_GRACE_PERIOD_FLAG = "SETTINGS_SECURITY_USE_GRACE_PERIOD_FLAG"
        const val SETTINGS_SECURITY_USE_COMPLETE_NOTIFICATIONS_FLAG = "SETTINGS_SECURITY_USE_COMPLETE_NOTIFICATIONS_FLAG"

        // New Session Manager
        const val SETTINGS_SESSION_MANAGER_SHOW_IP_ADDRESS = "SETTINGS_SESSION_MANAGER_SHOW_IP_ADDRESS"

        // other
        const val SETTINGS_MEDIA_SAVING_PERIOD_KEY = "SETTINGS_MEDIA_SAVING_PERIOD_KEY"
        private const val SETTINGS_MEDIA_SAVING_PERIOD_SELECTED_KEY = "SETTINGS_MEDIA_SAVING_PERIOD_SELECTED_KEY"

        // SC additions
        private const val SETTINGS_SINGLE_OVERVIEW = "SETTINGS_SINGLE_OVERVIEW"

        @Deprecated("Please append _DM or _GROUP")
        private const val SETTINGS_ROOM_UNREAD_KIND = "SETTINGS_ROOM_UNREAD_KIND"
        private const val SETTINGS_ROOM_UNREAD_KIND_DM = "SETTINGS_ROOM_UNREAD_KIND_DM"
        private const val SETTINGS_ROOM_UNREAD_KIND_GROUP = "SETTINGS_ROOM_UNREAD_KIND_GROUP"
        const val SETTINGS_UNIMPORTANT_COUNTER_BADGE = "SETTINGS_UNIMPORTANT_COUNTER_BADGE"
        const val SETTINGS_AGGREGATE_UNREAD_COUNTS = "SETTINGS_AGGREGATE_UNREAD_COUNTS"
        private const val SETTINGS_SIMPLIFIED_MODE = "SETTINGS_SIMPLIFIED_MODE"
        private const val SETTINGS_LABS_ALLOW_MARK_UNREAD = "SETTINGS_LABS_ALLOW_MARK_UNREAD"
        const val SETTINGS_ALLOW_URL_PREVIEW_IN_ENCRYPTED_ROOM_KEY = "SETTINGS_ALLOW_URL_PREVIEW_IN_ENCRYPTED_ROOM_KEY"
        private const val SETTINGS_USER_COLOR_MODE_DM = "SETTINGS_USER_COLOR_MODE_DM"
        private const val SETTINGS_USER_COLOR_MODE_DEFAULT = "SETTINGS_USER_COLOR_MODE_DEFAULT"
        private const val SETTINGS_USER_COLOR_MODE_PUBLIC_ROOM = "SETTINGS_USER_COLOR_MODE_PUBLIC_ROOM"
        private const val SETTINGS_OPEN_CHATS_AT_FIRST_UNREAD = "SETTINGS_OPEN_CHATS_AT_FIRST_UNREAD"
        const val SETTINGS_VOICE_MESSAGE = "SETTINGS_VOICE_MESSAGE"
        private const val SETTINGS_JUMP_TO_BOTTOM_ON_SEND = "SETTINGS_JUMP_TO_BOTTOM_ON_SEND"
        private const val SETTINGS_SPACE_MEMBERS_IN_SPACE_ROOMS = "SETTINGS_SPACE_MEMBERS_IN_SPACE_ROOMS"
        private const val SETTINGS_ENABLE_SPACE_PAGER = "SETTINGS_ENABLE_SPACE_PAGER"
        private const val SETTINGS_SPACE_PAGER_BAR_PREFER_SPACE = "SETTINGS_SPACE_PAGER_BAR_PREFER_SPACE"
        private const val SETTINGS_NOTIF_ONLY_ALERT_ONCE = "SETTINGS_NOTIF_ONLY_ALERT_ONCE"
        private const val SETTINGS_HIDE_CALL_BUTTONS = "SETTINGS_HIDE_CALL_BUTTONS"
        private const val SETTINGS_READ_RECEIPT_FOLLOWS_READ_MARKER = "SETTINGS_READ_RECEIPT_FOLLOWS_READ_MARKER"
        private const val SETTINGS_SHOW_OPEN_ANONYMOUS = "SETTINGS_SHOW_OPEN_ANONYMOUS"
        private const val SETTINGS_FLOATING_DATE = "SETTINGS_FLOATING_DATE"
        private const val SETTINGS_SPACE_BACK_NAVIGATION = "SETTINGS_SPACE_BACK_NAVIGATION"
        const val SETTINGS_FOLLOW_SYSTEM_LOCALE = "SETTINGS_FOLLOW_SYSTEM_LOCALE"
        const val SETTINGS_FORCE_ALLOW_BACKGROUND_SYNC = "SETTINGS_FORCE_ALLOW_BACKGROUND_SYNC"
        const val SETTINGS_ROOM_SORT_ORDER_NULL = "SETTINGS_ROOM_SORT_ORDER_NULL"
        const val SETTINGS_ROOM_SORT_ORDER_NON_NULL = "SETTINGS_ROOM_SORT_ORDER_NON_NULL"
        private const val SETTINGS_ENABLE_MEMBER_NAME_CLICK = "SETTINGS_ENABLE_MEMBER_NAME_CLICK"
        private const val SETTINGS_CLEAR_HIGHLIGHT_ON_SCROLL = "SETTINGS_CLEAR_HIGHLIGHT_ON_SCROLL"

        private const val DID_ASK_TO_ENABLE_SESSION_PUSH = "DID_ASK_TO_ENABLE_SESSION_PUSH"

        private const val MEDIA_SAVING_3_DAYS = 0
        private const val MEDIA_SAVING_1_WEEK = 1
        private const val MEDIA_SAVING_1_MONTH = 2
        private const val MEDIA_SAVING_FOREVER = 3

        private const val TAKE_PHOTO_VIDEO_MODE = "TAKE_PHOTO_VIDEO_MODE"

        private const val SETTINGS_LABS_ENABLE_LIVE_LOCATION = "SETTINGS_LABS_ENABLE_LIVE_LOCATION"

        private const val SETTINGS_LABS_ENABLE_ELEMENT_CALL_PERMISSION_SHORTCUTS = "SETTINGS_LABS_ENABLE_ELEMENT_CALL_PERMISSION_SHORTCUTS"

        // This key will be used to identify clients with the old thread support enabled io.element.thread
        const val SETTINGS_LABS_ENABLE_THREAD_MESSAGES_OLD_CLIENTS = "SETTINGS_LABS_ENABLE_THREAD_MESSAGES"

        // This key will be used to identify clients with the new thread support enabled m.thread
        const val SETTINGS_LABS_ENABLE_THREAD_MESSAGES = "SETTINGS_LABS_ENABLE_THREAD_MESSAGES_FINAL"
        const val SETTINGS_LABS_THREAD_MESSAGES_CHANGED_BY_USER = "SETTINGS_LABS_THREAD_MESSAGES_CHANGED_BY_USER"
        const val SETTINGS_THREAD_MESSAGES_SYNCED = "SETTINGS_THREAD_MESSAGES_SYNCED"

        // This key will be used to enable user for displaying live user info or not.
        const val SETTINGS_TIMELINE_SHOW_LIVE_SENDER_INFO = "SETTINGS_TIMELINE_SHOW_LIVE_SENDER_INFO"

        const val SETTINGS_UNVERIFIED_SESSIONS_ALERT_LAST_SHOWN_MILLIS = "SETTINGS_UNVERIFIED_SESSIONS_ALERT_LAST_SHOWN_MILLIS_"
        const val SETTINGS_NEW_LOGIN_ALERT_SHOWN_FOR_DEVICE = "SETTINGS_NEW_LOGIN_ALERT_SHOWN_FOR_DEVICE_"

        // Possible values for TAKE_PHOTO_VIDEO_MODE
        const val TAKE_PHOTO_VIDEO_MODE_ALWAYS_ASK = 0
        const val TAKE_PHOTO_VIDEO_MODE_PHOTO = 1
        const val TAKE_PHOTO_VIDEO_MODE_VIDEO = 2

        const val HAD_EXISTING_LEGACY_DATA = "HAD_EXISTING_LEGACY_DATA"
        const val IS_ON_RUST_CRYPTO = "IS_ON_RUST_CRYPTO"
        // Background sync modes

        // some preferences keys must be kept after a logout
        private val mKeysToKeepAfterLogout = listOf(
                SETTINGS_DEFAULT_MEDIA_COMPRESSION_KEY,
                SETTINGS_DEFAULT_MEDIA_SOURCE_KEY,
                SETTINGS_PLAY_SHUTTER_SOUND_KEY,

                SETTINGS_SEND_TYPING_NOTIF_KEY,
                SETTINGS_ALWAYS_SHOW_TIMESTAMPS_KEY,
                SETTINGS_12_24_TIMESTAMPS_KEY,
                SETTINGS_SHOW_READ_RECEIPTS_KEY,
                SETTINGS_SHOW_ROOM_MEMBER_STATE_EVENTS_KEY,
                SETTINGS_SHOW_JOIN_LEAVE_MESSAGES_KEY,
                SETTINGS_SHOW_AVATAR_DISPLAY_NAME_CHANGES_MESSAGES_KEY,
                SETTINGS_MEDIA_SAVING_PERIOD_KEY,
                SETTINGS_MEDIA_SAVING_PERIOD_SELECTED_KEY,
                SETTINGS_PREVIEW_MEDIA_BEFORE_SENDING_KEY,
                SETTINGS_SEND_MESSAGE_WITH_ENTER,
                SETTINGS_SHOW_EMOJI_KEYBOARD,

                SETTINGS_PIN_UNREAD_MESSAGES_PREFERENCE_KEY,
                SETTINGS_PIN_MISSED_NOTIFICATIONS_PREFERENCE_KEY,
                // Do not keep SETTINGS_LAZY_LOADING_PREFERENCE_KEY because the user may log in on a server which does not support lazy loading
                SETTINGS_DATA_SAVE_MODE_PREFERENCE_KEY,
                SETTINGS_START_ON_BOOT_PREFERENCE_KEY,
                SETTINGS_INTERFACE_TEXT_SIZE_KEY,
                SETTINGS_USE_JITSI_CONF_PREFERENCE_KEY,
                SETTINGS_NOTIFICATION_RINGTONE_PREFERENCE_KEY,
                SETTINGS_NOTIFICATION_RINGTONE_SELECTION_PREFERENCE_KEY,

                SETTINGS_ROOM_SETTINGS_LABS_END_TO_END_PREFERENCE_KEY,
                SETTINGS_CONTACTS_PHONEBOOK_COUNTRY_PREFERENCE_KEY,
                SETTINGS_INTERFACE_LANGUAGE_PREFERENCE_KEY,
                SETTINGS_BACKGROUND_SYNC_PREFERENCE_KEY,
                SETTINGS_ENABLE_BACKGROUND_SYNC_PREFERENCE_KEY,
                SETTINGS_SET_SYNC_TIMEOUT_PREFERENCE_KEY,
                SETTINGS_SET_SYNC_DELAY_PREFERENCE_KEY,

                SETTINGS_DEVELOPER_MODE_PREFERENCE_KEY,
                SETTINGS_LABS_SHOW_HIDDEN_EVENTS_PREFERENCE_KEY,
                SETTINGS_LABS_ALLOW_EXTENDED_LOGS,
                SETTINGS_DEVELOPER_MODE_FAIL_FAST_PREFERENCE_KEY,
                SETTINGS_ENABLE_MEMORY_LEAK_ANALYSIS_KEY,

                SETTINGS_USE_RAGE_SHAKE_KEY,
                SETTINGS_SECURITY_USE_FLAG_SECURE,
                SETTINGS_SECURITY_INCOGNITO_KEYBOARD_PREFERENCE_KEY,

                ShortcutsHandler.SHARED_PREF_KEY,

                DefaultLightweightSettingsStorage.MATRIX_SDK_SETTINGS_CONNECTION_TYPE,
                DefaultLightweightSettingsStorage.MATRIX_SDK_SETTINGS_CONNECTION_PROXY_HOST,
                DefaultLightweightSettingsStorage.MATRIX_SDK_SETTINGS_CONNECTION_PROXY_PORT,
                DefaultLightweightSettingsStorage.MATRIX_SDK_SETTINGS_CONNECTION_PROXY_TYPE,
                DefaultLightweightSettingsStorage.MATRIX_SDK_SETTINGS_CONNECTION_PROXY_AUTH_REQUIRED,
                DefaultLightweightSettingsStorage.MATRIX_SDK_SETTINGS_CONNECTION_PROXY_USERNAME,
                DefaultLightweightSettingsStorage.MATRIX_SDK_SETTINGS_CONNECTION_PROXY_PASSWORD
        )

        // Pref keys to include in rageshake if enabled
        val EXPERIMENTAL_PREF_IF_TRUE = arrayOf(
                SETTINGS_LABS_RICH_TEXT_EDITOR_KEY,
                SETTINGS_ENABLE_RICH_TEXT_FORMATTING_KEY,
                SETTINGS_LABS_NEW_APP_LAYOUT_KEY,
                SETTINGS_LABS_NEW_SESSION_MANAGER_KEY,
                SETTINGS_LABS_DEFERRED_DM_KEY,
        )

        // Pref keys to include in rageshake if disabled
        val EXPERIMENTAL_PREF_IF_FALSE = arrayOf(
                SETTINGS_LABS_ENABLE_THREAD_MESSAGES,
                SETTINGS_LABS_DEFERRED_DM_KEY,
        )
    }

    // SC rageshake extras
    fun getEnabledExperimentalSettings(): List<String> {
        return EXPERIMENTAL_PREF_IF_TRUE.filter {
            defaultPrefs.getBoolean(it, false)
        }
    }

    fun getDisabledExperimentalSettings(): List<String> {
        return EXPERIMENTAL_PREF_IF_FALSE.filterNot {
            defaultPrefs.getBoolean(it, true)
        }
    }

    /**
     * Allow subscribing and unsubscribing to configuration changes. This is
     * particularly useful when you need to be notified of a configuration change
     * in a background service, e.g. for the P2P demos.
     */
    fun subscribeToChanges(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        defaultPrefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unsubscribeToChanges(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        defaultPrefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    /**
     * Clear the preferences.
     */
    fun clearPreferences() {
        val keysToKeep = HashSet(mKeysToKeepAfterLogout)

        // homeserver urls
        keysToKeep.add(ServerUrlsRepository.HOME_SERVER_URL_PREF)
        keysToKeep.add(ServerUrlsRepository.IDENTITY_SERVER_URL_PREF)

        // theme
        keysToKeep.add(ThemeUtils.APPLICATION_THEME_KEY)

        // get all the existing keys
        val keys = defaultPrefs.all.keys

        // remove the one to keep
        keys.removeAll(keysToKeep)

        defaultPrefs.edit {
            for (key in keys) {
                remove(key)
            }
        }
    }

    private fun getDefault(@BoolRes resId: Int) = context.resources.getBoolean(resId)

    fun areNotificationEnabledForDevice(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_ENABLE_THIS_DEVICE_PREFERENCE_KEY, true)
    }

    fun setNotificationEnabledForDevice(enabled: Boolean) {
        defaultPrefs.edit {
            putBoolean(SETTINGS_ENABLE_THIS_DEVICE_PREFERENCE_KEY, enabled)
        }
    }

    fun developerMode(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_DEVELOPER_MODE_PREFERENCE_KEY, false)
    }

    fun developerShowDebugInfo(): Boolean {
        return developerMode() && defaultPrefs.getBoolean(SETTINGS_DEVELOPER_MODE_SHOW_INFO_ON_SCREEN_KEY, false)
    }

    fun shouldShowHiddenEvents(): Boolean {
        return developerMode() && defaultPrefs.getBoolean(SETTINGS_LABS_SHOW_HIDDEN_EVENTS_PREFERENCE_KEY, false)
    }

    fun setShouldShowHiddenEvents(shouldShow: Boolean) {
        defaultPrefs.edit(commit = true) { putBoolean(SETTINGS_LABS_SHOW_HIDDEN_EVENTS_PREFERENCE_KEY, shouldShow) }
    }

    fun swipeToReplyIsEnabled(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_LABS_ENABLE_SWIPE_TO_REPLY, true)
    }

    fun labShowCompleteHistoryInEncryptedRoom(): Boolean {
        return developerMode() && defaultPrefs.getBoolean(SETTINGS_LABS_SHOW_COMPLETE_HISTORY_IN_ENCRYPTED_ROOM, false)
    }

    fun labAllowedExtendedLogging(): Boolean {
        return developerMode() && defaultPrefs.getBoolean(SETTINGS_LABS_ALLOW_EXTENDED_LOGS, false)
    }

    fun labAddNotificationTab(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_LABS_UNREAD_NOTIFICATIONS_AS_TAB, false)
    }

    fun latexMathsIsEnabled(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_LABS_ENABLE_LATEX_MATHS, false)
    }

    fun failFast(): Boolean {
        return buildMeta.isDebug || (developerMode() && defaultPrefs.getBoolean(SETTINGS_DEVELOPER_MODE_FAIL_FAST_PREFERENCE_KEY, false))
    }

    fun enableMemoryLeakAnalysis(isEnabled: Boolean) {
        defaultPrefs.edit(commit = true) {
            putBoolean(SETTINGS_ENABLE_MEMORY_LEAK_ANALYSIS_KEY, isEnabled)
        }
    }

    fun isMemoryLeakAnalysisEnabled(): Boolean {
        return buildMeta.isDebug && defaultPrefs.getBoolean(SETTINGS_ENABLE_MEMORY_LEAK_ANALYSIS_KEY, false)
    }

    fun didAskUserToEnableSessionPush(): Boolean {
        return defaultPrefs.getBoolean(DID_ASK_TO_ENABLE_SESSION_PUSH, false)
    }

    fun setDidAskUserToEnableSessionPush() {
        defaultPrefs.edit {
            putBoolean(DID_ASK_TO_ENABLE_SESSION_PUSH, true)
        }
    }

    /**
     * Tells if the join and leave membership events should be shown in the messages list.
     *
     * @return true if the join and leave membership events should be shown in the messages list
     */
    fun showJoinLeaveMessages(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_SHOW_JOIN_LEAVE_MESSAGES_KEY, true)
    }

    fun setShowJoinLeaveMessages(shouldShow: Boolean) {
        defaultPrefs.edit(commit = true) { putBoolean(SETTINGS_SHOW_JOIN_LEAVE_MESSAGES_KEY, shouldShow) }
    }

    /**
     * Tells if the avatar and display name events should be shown in the messages list.
     *
     * @return true true if the avatar and display name events should be shown in the messages list.
     */
    fun showAvatarDisplayNameChangeMessages(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_SHOW_AVATAR_DISPLAY_NAME_CHANGES_MESSAGES_KEY, true)
    }

    fun setShowAvatarDisplayNameChangeMessages(shouldShow: Boolean) {
        defaultPrefs.edit(commit = true) { putBoolean(SETTINGS_SHOW_AVATAR_DISPLAY_NAME_CHANGES_MESSAGES_KEY, shouldShow) }
    }

    /**
     * Show all rooms in room directory.
     */
    fun showAllPublicRooms(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_ROOM_DIRECTORY_SHOW_ALL_PUBLIC_ROOMS, false)
    }

    /**
     * Update the notification ringtone.
     *
     * @param uri the new notification ringtone, or null for no RingTone
     */
    fun setNotificationRingTone(uri: Uri?) {
        defaultPrefs.edit {
            var value = ""

            if (null != uri) {
                value = uri.toString()

                if (value.startsWith("file://")) {
                    // it should never happen
                    // else android.os.FileUriExposedException will be triggered.
                    // see https://github.com/element-hq/riot-android/issues/1725
                    return
                }
            }

            putString(SETTINGS_NOTIFICATION_RINGTONE_PREFERENCE_KEY, value)
        }
    }

    /**
     * Provides the selected notification ring tone.
     *
     * @return the selected ring tone or null for no RingTone
     */
    fun getNotificationRingTone(): Uri? {
        val url = defaultPrefs.getString(SETTINGS_NOTIFICATION_RINGTONE_PREFERENCE_KEY, null)

        // the user selects "None"
        if (url == "") {
            return null
        }

        var uri: Uri? = null

        // https://github.com/element-hq/riot-android/issues/1725
        if (null != url && !url.startsWith("file://")) {
            try {
                uri = url.toUri()
            } catch (e: Exception) {
                Timber.e(e, "## getNotificationRingTone() : Uri.parse failed")
            }
        }

        if (null == uri) {
            uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }

        Timber.v("## getNotificationRingTone() returns $uri")
        return uri
    }

    /**
     * Provide the notification ringtone filename.
     *
     * @return the filename or null if "None" is selected
     */
    fun getNotificationRingToneName(): String? {
        val toneUri = getNotificationRingTone() ?: return null

        try {
            val proj = arrayOf(MediaStore.Audio.Media.DISPLAY_NAME)
            return context.contentResolver.query(toneUri, proj, null, null, null)?.use {
                val columnIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                it.moveToFirst()
                it.getString(columnIndex)
            }
        } catch (e: Exception) {
            Timber.e(e, "## getNotificationRingToneName() failed")
        }

        return null
    }

    /**
     * Tells if the application is started on boot.
     *
     * @return true if the application must be started on boot
     */
    fun autoStartOnBoot(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_START_ON_BOOT_PREFERENCE_KEY, true)
    }

    /**
     * Tells if the application is started on boot.
     *
     * @param value true to start the application on boot
     */
    fun setAutoStartOnBoot(value: Boolean) {
        defaultPrefs.edit {
            putBoolean(SETTINGS_START_ON_BOOT_PREFERENCE_KEY, value)
        }
    }

    /**
     * Provides the selected saving period.
     *
     * @return the selected period
     */
    fun getSelectedMediasSavingPeriod(): Int {
        return defaultPrefs.getInt(SETTINGS_MEDIA_SAVING_PERIOD_SELECTED_KEY, MEDIA_SAVING_1_WEEK)
    }

    /**
     * Updates the selected saving period.
     *
     * @param index the selected period index
     */
    fun setSelectedMediasSavingPeriod(index: Int) {
        defaultPrefs.edit {
            putInt(SETTINGS_MEDIA_SAVING_PERIOD_SELECTED_KEY, index)
        }
    }

    /**
     * Provides the selected saving period.
     *
     * @return the selected period
     */
    fun getSelectedMediasSavingPeriodString(): String {
        return when (getSelectedMediasSavingPeriod()) {
            MEDIA_SAVING_3_DAYS -> stringProvider.getString(CommonStrings.media_saving_period_3_days)
            MEDIA_SAVING_1_WEEK -> stringProvider.getString(CommonStrings.media_saving_period_1_week)
            MEDIA_SAVING_1_MONTH -> stringProvider.getString(CommonStrings.media_saving_period_1_month)
            MEDIA_SAVING_FOREVER -> stringProvider.getString(CommonStrings.media_saving_period_forever)
            else -> "?"
        }
    }

    /**
     * Tells if the markdown is enabled.
     *
     * @return true if the markdown is enabled
     */
    fun isMarkdownEnabled(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_ENABLE_MARKDOWN_KEY, false)
    }

    /**
     * Update the markdown enable status.
     *
     * @param isEnabled true to enable the markdown
     */
    fun setMarkdownEnabled(isEnabled: Boolean) {
        defaultPrefs.edit {
            putBoolean(SETTINGS_ENABLE_MARKDOWN_KEY, isEnabled)
        }
    }

    /**
     * Tells if text formatting is enabled within the rich text editor.
     *
     * @return true if the text formatting is enabled
     */
    fun isTextFormattingEnabled(): Boolean =
            defaultPrefs.getBoolean(SETTINGS_ENABLE_RICH_TEXT_FORMATTING_KEY, true)

    /**
     * Update whether text formatting is enabled within the rich text editor.
     *
     * @param isEnabled true to enable the text formatting
     */
    fun setTextFormattingEnabled(isEnabled: Boolean) =
            defaultPrefs.edit {
                putBoolean(SETTINGS_ENABLE_RICH_TEXT_FORMATTING_KEY, isEnabled)
            }

    /**
     * Tells if a confirmation dialog should be displayed before staring a call.
     */
    fun preventAccidentalCall(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_CALL_PREVENT_ACCIDENTAL_CALL_KEY, true)
    }

    /**
     * Tells if turn.matrix should be used during calls as a fallback
     */
    fun useFallbackTurnServer(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_CALL_USE_FALLBACK_CALL_ASSIST_SERVER_KEY, false)
    }

    /**
     * Tells if the read receipts should be shown.
     *
     * @return true if the read receipts should be shown
     */
    fun showReadReceipts(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_SHOW_READ_RECEIPTS_KEY, true)
    }

    /**
     * Tells if the redacted message should be shown.
     *
     * @return true if the redacted should be shown
     */
    fun showRedactedMessages(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_SHOW_REDACTED_KEY, false)
    }

    fun setShowRedactedMessages(shouldShow: Boolean) {
        return defaultPrefs.edit(commit = true) { putBoolean(SETTINGS_SHOW_REDACTED_KEY, shouldShow) }
    }

    /**
     * Tells if the help on room list should be shown.
     *
     * @return true if the help on room list should be shown
     */
    fun shouldShowLongClickOnRoomHelp(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_SHOULD_SHOW_HELP_ON_ROOM_LIST_KEY, false)
    }

    /**
     * Prevent help on room list to be shown again.
     */
    fun neverShowLongClickOnRoomHelpAgain() {
        defaultPrefs.edit {
            putBoolean(SETTINGS_SHOULD_SHOW_HELP_ON_ROOM_LIST_KEY, false)
        }
    }

    /**
     * Tells if the message timestamps must be always shown.
     *
     * @return true if the message timestamps must be always shown
     */
    fun alwaysShowTimeStamps(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_ALWAYS_SHOW_TIMESTAMPS_KEY, false)
    }

    /**
     * Tells if animated image attachments should automatically play their animation in the timeline.
     *
     * @return true if animated image attachments should automatically play their animation in the timeline
     */
    fun autoplayAnimatedImages(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_AUTOPLAY_ANIMATED_IMAGES, true)
    }

    /**
     * Tells if the typing notifications should be sent.
     *
     * @return true to send the typing notifs
     */
    fun sendTypingNotifs(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_SEND_TYPING_NOTIF_KEY, true)
    }

    /**
     * Tells if the user wants to see URL previews in the timeline.
     *
     * @return true if the user wants to see URL previews in the timeline
     */
    fun showUrlPreviews(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_SHOW_URL_PREVIEW_KEY, true)
    }

    /**
     * Tells if the user wants to see URL previews in encrypted rooms as well
     *
     * @return true if the user wants to see URL previews in encrypted rooms
     */
    fun allowUrlPreviewsInEncryptedRooms(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_ALLOW_URL_PREVIEW_IN_ENCRYPTED_ROOM_KEY, false)
    }

    /**
     * Tells if message should be send by pressing enter on the soft keyboard.
     *
     * @return true to send message with enter
     */
    fun sendMessageWithEnter(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_SEND_MESSAGE_WITH_ENTER, false)
    }

    /**
     * Tells if the emoji keyboard button should be visible or not.
     *
     * @return true to show emoji keyboard button.
     */
    fun showEmojiKeyboard(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_SHOW_EMOJI_KEYBOARD, true)
    }

    /**
     * Tells if the timeline messages should be shown in a bubble or not.
     *
     * @return true to show timeline message in bubble.
     */
    /* SC: use BubbleThemeUtils instead
    fun useMessageBubblesLayout(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_INTERFACE_BUBBLE_KEY, getDefault(im.vector.app.config.R.bool.settings_interface_bubble_default))
    }
     */
    fun useElementMessageBubblesLayout(): Boolean {
        return bubbleThemeUtils.getBubbleStyle() == BubbleThemeUtils.BUBBLE_STYLE_ELEMENT
    }

    /**
     * Tells if user should always appear offline or not.
     *
     * @return true if user should always appear offline
     */
    fun userAlwaysAppearsOffline(): Boolean {
        return defaultPrefs.getBoolean(
                SETTINGS_PRESENCE_USER_ALWAYS_APPEARS_OFFLINE,
                getDefault(im.vector.app.config.R.bool.settings_presence_user_always_appears_offline_default)
        )
    }

    /**
     * Update the rage shake enabled status.
     *
     * @param isEnabled true to enable rage shake.
     */
    fun setRageshakeEnabled(isEnabled: Boolean) {
        defaultPrefs.edit {
            putBoolean(SETTINGS_USE_RAGE_SHAKE_KEY, isEnabled)
        }
    }

    /**
     * Tells if the rage shake is used.
     *
     * @return true if the rage shake is used
     */
    fun useRageshake(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_USE_RAGE_SHAKE_KEY, false)
    }

    /**
     * Get the rage shake sensitivity.
     */
    fun getRageshakeSensitivity(): Int {
        return defaultPrefs.getInt(SETTINGS_RAGE_SHAKE_DETECTION_THRESHOLD_KEY, ShakeDetector.SENSITIVITY_MEDIUM)
    }

    /**
     * The user does not allow screenshots of the application.
     */
    fun useFlagSecure(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_SECURITY_USE_FLAG_SECURE, false)
    }

    // SC addition
    fun combinedOverview(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_SINGLE_OVERVIEW, true)
    }

    fun enableOverviewTabs(): Boolean {
        return labAddNotificationTab() || !combinedOverview()
    }

    // SC addition
    private fun roomUnreadKind(key: String): Int {
        val default = RoomSummary.UNREAD_KIND_ORIGINAL_CONTENT
        val kind = defaultPrefs.getString(key, default.toString())
        return try {
            Integer.parseInt(kind!!)
        } catch (e: Exception) {
            default
        }
    }

    override fun roomUnreadKind(isDirect: Boolean): Int {
        return if (isDirect) {
            roomUnreadKindDm()
        } else {
            roomUnreadKindGroup()
        }
    }

    private fun roomUnreadKindDm(): Int {
        return roomUnreadKind(SETTINGS_ROOM_UNREAD_KIND_DM)
    }

    private fun roomUnreadKindGroup(): Int {
        return roomUnreadKind(SETTINGS_ROOM_UNREAD_KIND_GROUP)
    }

    // SC addition
    fun shouldShowUnimportantCounterBadge(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_UNIMPORTANT_COUNTER_BADGE, true)
    }

    // SC additions - for spaces/categories: whether to count unread chats, or messages
    override fun aggregateUnreadRoomCounts(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_AGGREGATE_UNREAD_COUNTS, true)
    }

    override fun includeSpaceMembersAsSpaceRooms(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_SPACE_MEMBERS_IN_SPACE_ROOMS, false)
    }

    private var prevToast: Toast? = null
    override fun annoyDevelopersWithToast(text: String) {
        if (developerShowDebugInfo()) {
            prevToast?.cancel()
            prevToast = Toast.makeText(context, text, Toast.LENGTH_LONG).also {
                it.show()
            }
        }
    }

    // SC addition
    fun simplifiedMode(): Boolean {
        return false
    }

    fun needsSimplifiedModePrompt(): Boolean {
        return false
    }

    //TODO: remove this
    @Suppress("UNUSED_PARAMETER")
    fun setSimplifiedMode(simplified: Boolean) {
        // no-op
    }

    // SC addition
    fun labAllowMarkUnread(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_LABS_ALLOW_MARK_UNREAD, true)
    }

    // SC addition
    @Suppress("DEPRECATION")
    fun scPreferenceUpdate() {
        if (defaultPrefs.contains(SETTINGS_ROOM_UNREAD_KIND)) {
            // Migrate to split setting for DMs and groups
            val unreadKindSetting = roomUnreadKind(SETTINGS_ROOM_UNREAD_KIND).toString()
            defaultPrefs
                    .edit {
                        putString(SETTINGS_ROOM_UNREAD_KIND_DM, unreadKindSetting)
                        putString(SETTINGS_ROOM_UNREAD_KIND_GROUP, unreadKindSetting)
                        remove(SETTINGS_ROOM_UNREAD_KIND)
                    }
        }
    }

    // SC addition
    fun userColorMode(isDirect: Boolean, isPublic: Boolean): String {
        return defaultPrefs.getString(
                when {
                    isPublic -> SETTINGS_USER_COLOR_MODE_PUBLIC_ROOM
                    isDirect -> SETTINGS_USER_COLOR_MODE_DM
                    else -> SETTINGS_USER_COLOR_MODE_DEFAULT
                }, MatrixItemColorProvider.USER_COLORING_DEFAULT
        ) ?: MatrixItemColorProvider.USER_COLORING_DEFAULT
    }

    fun canOverrideUserColors(): Boolean {
        return MatrixItemColorProvider.USER_COLORING_FROM_ID in listOf(
                userColorMode(isDirect = false, isPublic = false),
                userColorMode(isDirect = false, isPublic = true),
                userColorMode(isDirect = true, isPublic = false),
                userColorMode(isDirect = true, isPublic = true),
        )
    }

    // SC addition
    fun loadRoomAtFirstUnread(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_OPEN_CHATS_AT_FIRST_UNREAD, false)
    }

    // Element removed this, SC added it back (but this time, default to true)
    fun useVoiceMessage(): Boolean {
        // Voice messages crash on SDK 21
        return Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP && defaultPrefs.getBoolean(SETTINGS_VOICE_MESSAGE, true)
    }

    // SC addition
    fun jumpToBottomOnSend(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_JUMP_TO_BOTTOM_ON_SEND, true)
    }

    // SC addition
    fun enableSpacePager(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_ENABLE_SPACE_PAGER, false)
    }

    // SC addition
    fun preferSpecificSpacePagerSpace(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_SPACE_PAGER_BAR_PREFER_SPACE, false)
    }

    // SC addition
    fun onlyAlertOnce(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_NOTIF_ONLY_ALERT_ONCE, false)
    }

    // SC addition
    fun hideCallButtons(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_HIDE_CALL_BUTTONS, false)
    }

    // SC addition
    fun readReceiptFollowsReadMarker(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_READ_RECEIPT_FOLLOWS_READ_MARKER, false)
    }

    // SC addition
    fun showOpenAnonymous(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_SHOW_OPEN_ANONYMOUS, false)
    }

    // SC addition
    fun floatingDate(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_FLOATING_DATE, true)
    }

    // SC addition
    fun spaceBackNavigation(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_SPACE_BACK_NAVIGATION, false)
    }

    // SC addition
    fun enableMemberNameClick(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_ENABLE_MEMBER_NAME_CLICK, true)
    }

    // SC addition
    fun clearHighlightOnScroll(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_CLEAR_HIGHLIGHT_ON_SCROLL, false)
    }

    /**
     * I likely do more fresh installs of the app than anyone else, so a shortcut to change some of the default settings to
     * my preferred values can safe me some time
     */
    fun applyScDefaultValues() {
        defaultPrefs.edit {
            putBoolean(SETTINGS_SIMPLIFIED_MODE, false)
            putString(SETTINGS_USER_COLOR_MODE_PUBLIC_ROOM, MatrixItemColorProvider.USER_COLORING_FROM_PL)
            putString(SETTINGS_USER_COLOR_MODE_DEFAULT, MatrixItemColorProvider.USER_COLORING_FROM_PL)
            putString(SETTINGS_USER_COLOR_MODE_DM, MatrixItemColorProvider.USER_COLORING_UNIFORM)
            putString(SETTINGS_ROOM_UNREAD_KIND_DM, RoomSummary.UNREAD_KIND_ORIGINAL_CONTENT.toString())
            putString(SETTINGS_ROOM_UNREAD_KIND_GROUP, RoomSummary.UNREAD_KIND_ORIGINAL_CONTENT.toString())
            putBoolean(SETTINGS_UNIMPORTANT_COUNTER_BADGE, true)
            putBoolean(SETTINGS_PREF_SPACE_SHOW_ALL_ROOM_IN_HOME, true)
            putBoolean(SETTINGS_OPEN_CHATS_AT_FIRST_UNREAD, true)
            putBoolean(SETTINGS_ALLOW_URL_PREVIEW_IN_ENCRYPTED_ROOM_KEY, true)
            putBoolean(SETTINGS_LABS_ALLOW_MARK_UNREAD, true)
            //.putBoolean(SETTINGS_LABS_ENABLE_SWIPE_TO_REPLY, false)
            putBoolean(SETTINGS_VOICE_MESSAGE, false)
            putBoolean(SETTINGS_USE_RAGE_SHAKE_KEY, true)
            putBoolean(SETTINGS_AGGREGATE_UNREAD_COUNTS, false)
            putBoolean(SETTINGS_ENABLE_SPACE_PAGER, true)
            putBoolean(SETTINGS_READ_RECEIPT_FOLLOWS_READ_MARKER, true)
            putBoolean(SETTINGS_SHOW_OPEN_ANONYMOUS, true)
            putBoolean(SETTINGS_FLOATING_DATE, true)
            putBoolean(SETTINGS_FOLLOW_SYSTEM_LOCALE, true)
            putBoolean(SETTINGS_ENABLE_MEMBER_NAME_CLICK, false)
            putBoolean(SETTINGS_CLEAR_HIGHLIGHT_ON_SCROLL, true)
            putBoolean(SETTINGS_SPACE_PAGER_BAR_PREFER_SPACE, true)
        }
    }

    // Some more quick settings
    fun setVoiceMessageButtonEnabled(enabled: Boolean) = defaultPrefs.edit(commit = true) { putBoolean(SETTINGS_VOICE_MESSAGE, enabled) }
    fun setEmojiButtonEnabled(enabled: Boolean) = defaultPrefs.edit(commit = true) { putBoolean(SETTINGS_SHOW_EMOJI_KEYBOARD, enabled) }
    fun setRichEditorEnabled(enabled: Boolean) = defaultPrefs.edit(commit = true) { putBoolean(SETTINGS_LABS_RICH_TEXT_EDITOR_KEY, enabled) }

    /** Whether the keyboard should disable personalized learning. */
    fun useIncognitoKeyboard(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_SECURITY_INCOGNITO_KEYBOARD_PREFERENCE_KEY, false)
    }

    /**
     * The user enable protecting app access with pin code.
     * Currently we use the pin code store to know if the pin is enabled, so this is not used
     */
    fun useFlagPinCode(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_SECURITY_USE_PIN_CODE_FLAG, false)
    }

    fun setUseBiometricToUnlock(value: Boolean) {
        defaultPrefs.edit { putBoolean(SETTINGS_SECURITY_USE_BIOMETRICS_FLAG, value) }
    }

    fun useBiometricsToUnlock(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_SECURITY_USE_BIOMETRICS_FLAG, true)
    }

    fun useGracePeriod(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_SECURITY_USE_GRACE_PERIOD_FLAG, true)
    }

    fun chatEffectsEnabled(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_ENABLE_CHAT_EFFECTS, true)
    }

    fun directShareEnabled(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_ENABLE_DIRECT_SHARE, true)
    }

    /**
     * Return true if Pin code is disabled, or if user set the settings to see full notification content.
     */
    fun useCompleteNotificationFormat(): Boolean {
        return !useFlagPinCode() ||
                defaultPrefs.getBoolean(SETTINGS_SECURITY_USE_COMPLETE_NOTIFICATIONS_FLAG, true)
    }

    fun backgroundSyncTimeOut(): Int {
        return tryOrNull {
            // The xml pref is saved as a string so use getString and parse
            defaultPrefs.getString(SETTINGS_SET_SYNC_TIMEOUT_PREFERENCE_KEY, null)?.toInt()
        } ?: BackgroundSyncMode.DEFAULT_SYNC_TIMEOUT_SECONDS
    }

    fun setBackgroundSyncTimeout(timeInSecond: Int) {
        defaultPrefs
                .edit {
                    putString(SETTINGS_SET_SYNC_TIMEOUT_PREFERENCE_KEY, timeInSecond.toString())
                }
    }

    fun backgroundSyncDelay(): Int {
        return tryOrNull {
            // The xml pref is saved as a string so use getString and parse
            defaultPrefs.getString(SETTINGS_SET_SYNC_DELAY_PREFERENCE_KEY, null)?.toInt()
        } ?: BackgroundSyncMode.DEFAULT_SYNC_DELAY_SECONDS
    }

    fun setBackgroundSyncDelay(timeInSecond: Int) {
        defaultPrefs
                .edit {
                    putString(SETTINGS_SET_SYNC_DELAY_PREFERENCE_KEY, timeInSecond.toString())
                }
    }

    fun isBackgroundSyncEnabled(): Boolean {
        return getFdroidSyncBackgroundMode() != BackgroundSyncMode.FDROID_BACKGROUND_SYNC_MODE_DISABLED
    }

    // SC addition
    fun forceAllowBackgroundSync(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_FORCE_ALLOW_BACKGROUND_SYNC, false)
    }

    fun setFdroidSyncBackgroundMode(mode: BackgroundSyncMode) {
        defaultPrefs
                .edit {
                    putString(SETTINGS_FDROID_BACKGROUND_SYNC_MODE, mode.name)
                }
    }

    fun getFdroidSyncBackgroundMode(): BackgroundSyncMode {
        return try {
            val strPref = defaultPrefs
                    .getString(SETTINGS_FDROID_BACKGROUND_SYNC_MODE, BackgroundSyncMode.FDROID_BACKGROUND_SYNC_MODE_FOR_BATTERY.name)
            BackgroundSyncMode.entries.firstOrNull { it.name == strPref } ?: BackgroundSyncMode.FDROID_BACKGROUND_SYNC_MODE_FOR_BATTERY
        } catch (e: Throwable) {
            BackgroundSyncMode.FDROID_BACKGROUND_SYNC_MODE_FOR_BATTERY
        }
    }

    fun labsAutoReportUISI(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_LABS_AUTO_REPORT_UISI, false)
    }

    fun prefSpacesShowAllRoomInHome(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_PREF_SPACE_SHOW_ALL_ROOM_IN_HOME, true)
    }

    /*
     * Photo / video picker
     */
    fun getTakePhotoVideoMode(): Int {
        return defaultPrefs.getInt(TAKE_PHOTO_VIDEO_MODE, TAKE_PHOTO_VIDEO_MODE_ALWAYS_ASK)
    }

    fun setTakePhotoVideoMode(mode: Int) {
        return defaultPrefs.edit {
            putInt(TAKE_PHOTO_VIDEO_MODE, mode)
        }
    }

    fun labsEnableLiveLocation(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_LABS_ENABLE_LIVE_LOCATION, false)
    }

    fun setLiveLocationLabsEnabled(isEnabled: Boolean) {
        defaultPrefs.edit {
            putBoolean(SETTINGS_LABS_ENABLE_LIVE_LOCATION, isEnabled)
        }
    }

    fun labsEnableElementCallPermissionShortcuts(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_LABS_ENABLE_ELEMENT_CALL_PERMISSION_SHORTCUTS, false)
    }

    /**
     * Indicates whether or not thread messages are enabled.
     */
    fun areThreadMessagesEnabled(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_LABS_ENABLE_THREAD_MESSAGES, getDefault(im.vector.app.config.R.bool.settings_labs_thread_messages_default))
    }

    /**
     * Manually sets thread messages enabled, useful for migrating users from io.element.thread.
     */
    fun setThreadMessagesEnabled() {
        defaultPrefs
                .edit {
                    putBoolean(SETTINGS_LABS_ENABLE_THREAD_MESSAGES, true)
                }
    }

    /**
     * Indicates whether or not user changed threads flag manually. We need this to not force flag to be enabled on app start.
     * Should be removed when Threads flag will be removed
     */
    fun wasThreadFlagChangedManually(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_LABS_THREAD_MESSAGES_CHANGED_BY_USER, false)
    }

    /**
     * Sets the flag to indicate that user changed threads flag (e.g. disabled them).
     */
    fun setThreadFlagChangedManually() {
        defaultPrefs
                .edit {
                    putBoolean(SETTINGS_LABS_THREAD_MESSAGES_CHANGED_BY_USER, true)
                }
    }

    /**
     * Indicates whether or not the user will be notified about the new thread support.
     * We should notify the user only if he had old thread support enabled.
     */
    fun shouldNotifyUserAboutThreads(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_LABS_ENABLE_THREAD_MESSAGES_OLD_CLIENTS, false)
    }

    /**
     * Indicates that the user have been notified about threads migration.
     */
    fun userNotifiedAboutThreads() {
        defaultPrefs
                .edit {
                    putBoolean(SETTINGS_LABS_ENABLE_THREAD_MESSAGES_OLD_CLIENTS, false)
                }
    }

    /**
     * Indicates whether or not we should clear cache for threads migration.
     * Default value is true, for fresh installs and updates
     */
    fun shouldMigrateThreads(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_THREAD_MESSAGES_SYNCED, true)
    }

    /**
     * Indicates that there no longer threads migration needed.
     */
    fun setShouldMigrateThreads(shouldMigrate: Boolean) {
        defaultPrefs
                .edit {
                    putBoolean(SETTINGS_THREAD_MESSAGES_SYNCED, shouldMigrate)
                }
    }

    /**
     * Sets the space backstack that is used for up navigation.
     * This needs to be persisted because navigating up through spaces should work across sessions.
     *
     * Only the IDs of the spaces are stored.
     */
    fun setSpaceBackstack(spaceBackstack: List<String?>) {
        if (!spaceBackNavigation()) {
            // Don't build a huge stack that'll never get cleared
            return
        }
        val spaceIdsJoined = spaceBackstack.takeIf { it.isNotEmpty() }?.joinToString(",")
        defaultPrefs.edit { putString(SETTINGS_PERSISTED_SPACE_BACKSTACK, spaceIdsJoined) }
    }

    /**
     * Gets the space backstack used for up navigation.
     */
    fun getSpaceBackstack(): List<String?> {
        val spaceIdsJoined = defaultPrefs.getString(SETTINGS_PERSISTED_SPACE_BACKSTACK, null)
        return spaceIdsJoined?.takeIf { it.isNotEmpty() }?.split(",").orEmpty()
    }

    /**
     * Indicates whether or not new app layout is enabled.
     */
    fun isNewAppLayoutEnabled(): Boolean {
        return vectorFeatures.isNewAppLayoutFeatureEnabled() &&
                defaultPrefs.getBoolean(SETTINGS_LABS_NEW_APP_LAYOUT_KEY, getDefault(im.vector.app.config.R.bool.settings_labs_new_app_layout_default))
    }

    fun setNewAppLayoutEnabled(enabled: Boolean) {
        defaultPrefs.edit { putBoolean(SETTINGS_LABS_NEW_APP_LAYOUT_KEY, enabled) }
    }

    /**
     * Indicates whether or not deferred DMs are enabled.
     */
    fun isDeferredDmEnabled(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_LABS_DEFERRED_DM_KEY, getDefault(im.vector.app.config.R.bool.settings_labs_deferred_dm_default))
    }

    /**
     * Indicates whether or not new session manager screens are enabled.
     */
    fun isNewSessionManagerEnabled(): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_LABS_NEW_SESSION_MANAGER_KEY, getDefault(im.vector.app.config.R.bool.settings_labs_new_session_manager_default))
    }

    /**
     * Indicates whether or not client info recording is enabled.
     */
    fun isClientInfoRecordingEnabled(): Boolean {
        return defaultPrefs.getBoolean(
                SETTINGS_LABS_CLIENT_INFO_RECORDING_KEY,
                getDefault(
                        im.vector.app.config.R.bool.settings_labs_client_info_recording_default
                )
        )
    }

    fun showLiveSenderInfo(): Boolean {
        return defaultPrefs.getBoolean(
                SETTINGS_TIMELINE_SHOW_LIVE_SENDER_INFO,
                getDefault(im.vector.app.config.R.bool.settings_timeline_show_live_sender_info_default)
        )
    }

    fun isRichTextEditorEnabled(): Boolean {
        return defaultPrefs.getBoolean(
                SETTINGS_LABS_RICH_TEXT_EDITOR_KEY,
                getDefault(im.vector.app.config.R.bool.settings_labs_rich_text_editor_default)
        )
    }

    fun isVoiceBroadcastEnabled(): Boolean {
        return vectorFeatures.isVoiceBroadcastEnabled() &&
                defaultPrefs.getBoolean(
                        SETTINGS_LABS_VOICE_BROADCAST_KEY,
                        getDefault(im.vector.app.config.R.bool.settings_labs_enable_voice_broadcast_default)
                )
    }

    fun showIpAddressInSessionManagerScreens(): Boolean {
        return defaultPrefs.getBoolean(
                SETTINGS_SESSION_MANAGER_SHOW_IP_ADDRESS,
                getDefault(im.vector.app.config.R.bool.settings_session_manager_show_ip_address)
        )
    }

    fun setIpAddressVisibilityInDeviceManagerScreens(isVisible: Boolean) {
        defaultPrefs.edit {
            putBoolean(SETTINGS_SESSION_MANAGER_SHOW_IP_ADDRESS, isVisible)
        }
    }

    fun getUnverifiedSessionsAlertLastShownMillis(deviceId: String): Long {
        return defaultPrefs.getLong(SETTINGS_UNVERIFIED_SESSIONS_ALERT_LAST_SHOWN_MILLIS + deviceId, 0)
    }

    fun setUnverifiedSessionsAlertLastShownMillis(deviceId: String, lastShownMillis: Long) {
        defaultPrefs.edit {
            putLong(SETTINGS_UNVERIFIED_SESSIONS_ALERT_LAST_SHOWN_MILLIS + deviceId, lastShownMillis)
        }
    }

    fun isNewLoginAlertShownForDevice(deviceId: String): Boolean {
        return defaultPrefs.getBoolean(SETTINGS_NEW_LOGIN_ALERT_SHOWN_FOR_DEVICE + deviceId, false)
    }

    fun setNewLoginAlertShownForDevice(deviceId: String) {
        defaultPrefs.edit {
            putBoolean(SETTINGS_NEW_LOGIN_ALERT_SHOWN_FOR_DEVICE + deviceId, true)
        }
    }

    fun hadExistingLegacyData(): Boolean {
        return defaultPrefs.getBoolean(HAD_EXISTING_LEGACY_DATA, false)
    }

    fun setHadExistingLegacyData(had: Boolean) {
        defaultPrefs.edit {
            putBoolean(HAD_EXISTING_LEGACY_DATA, had)
        }
    }

    fun isOnRustCrypto(): Boolean {
        return defaultPrefs.getBoolean(IS_ON_RUST_CRYPTO, false)
    }

    fun setIsOnRustCrypto(boolean: Boolean) {
        defaultPrefs.edit {
            putBoolean(IS_ON_RUST_CRYPTO, boolean)
        }
    }
}
