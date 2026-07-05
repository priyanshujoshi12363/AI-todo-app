package com.example.voicetodo.data

import android.content.Context
import android.provider.ContactsContract
import android.util.Log

/** Looks up a phone number for a spoken name using the device's contacts. */
object ContactResolver {

    /** Returns the best-matching phone number for [name], or null if none/no permission. */
    fun findNumber(context: Context, name: String): String? {
        val query = name.trim()
        if (query.isEmpty()) return null
        return try {
            val resolver = context.contentResolver
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            // Match names that start with, or contain, the spoken word.
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val cursor = resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                arrayOf("%$query%"),
                null
            ) ?: return null

            cursor.use { c ->
                val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                var firstNumber: String? = null
                while (c.moveToNext()) {
                    val display = c.getString(nameIdx).orEmpty()
                    val number = c.getString(numIdx)?.replace("\\s".toRegex(), "")
                    if (number.isNullOrBlank()) continue
                    if (firstNumber == null) firstNumber = number
                    // Prefer an exact (case-insensitive) name match.
                    if (display.equals(query, ignoreCase = true)) return number
                }
                firstNumber
            }
        } catch (e: SecurityException) {
            Log.w("ContactResolver", "No Contacts permission")
            null
        } catch (e: Exception) {
            Log.e("ContactResolver", "Contact lookup failed", e)
            null
        }
    }
}
