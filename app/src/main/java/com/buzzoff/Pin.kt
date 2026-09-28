package com.buzzoff

import android.content.Context
import androidx.core.content.edit
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/** Stores a salted hash of the "I'm up" PIN, never the PIN itself. */
object Pin {
    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("pin", Context.MODE_PRIVATE)

    fun isSet(ctx: Context): Boolean = prefs(ctx).contains("hash")

    fun set(ctx: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(ctx).edit {
            putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            putString("hash", hash(salt, pin))
        }
    }

    fun check(ctx: Context, pin: String): Boolean {
        val p = prefs(ctx)
        val salt = p.getString("salt", null) ?: return false
        val expected = p.getString("hash", null) ?: return false
        val actual = hash(Base64.decode(salt, Base64.NO_WRAP), pin)
        return MessageDigest.isEqual(actual.toByteArray(), expected.toByteArray())
    }

    private fun hash(salt: ByteArray, pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").run {
            update(salt)
            digest(pin.toByteArray())
        }
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }
}
