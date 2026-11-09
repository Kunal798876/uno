package com.example.uno.network

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * Manages guest identity and reconnect token securely on device.
 * No account, login, signup, or phone number required.
 */
class GuestSessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("uno_guest_session_prefs", Context.MODE_PRIVATE)

    var guestId: String
        get() {
            var id = prefs.getString(KEY_GUEST_ID, null)
            if (id == null) {
                id = "guest_" + UUID.randomUUID().toString().substring(0, 8)
                prefs.edit().putString(KEY_GUEST_ID, id).apply()
            }
            return id
        }
        set(value) {
            prefs.edit().putString(KEY_GUEST_ID, value).apply()
        }

    var reconnectToken: String
        get() {
            var token = prefs.getString(KEY_RECONNECT_TOKEN, null)
            if (token == null) {
                token = "token_" + UUID.randomUUID().toString()
                prefs.edit().putString(KEY_RECONNECT_TOKEN, token).apply()
            }
            return token
        }
        set(value) {
            prefs.edit().putString(KEY_RECONNECT_TOKEN, value).apply()
        }

    var lastRoomCode: String?
        get() = prefs.getString(KEY_LAST_ROOM_CODE, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_ROOM_CODE, value).apply()
        }

    var nickname: String
        get() = prefs.getString(KEY_NICKNAME, "Player") ?: "Player"
        set(value) {
            prefs.edit().putString(KEY_NICKNAME, value).apply()
        }

    var avatar: String
        get() = prefs.getString(KEY_AVATAR, "🧔") ?: "🧔"
        set(value) {
            prefs.edit().putString(KEY_AVATAR, value).apply()
        }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_LAST_ROOM_CODE)
            .apply()
    }

    companion object {
        private const val KEY_GUEST_ID = "guest_id"
        private const val KEY_RECONNECT_TOKEN = "reconnect_token"
        private const val KEY_LAST_ROOM_CODE = "last_room_code"
        private const val KEY_NICKNAME = "guest_nickname"
        private const val KEY_AVATAR = "guest_avatar"
    }
}
