package com.echocall.app.util

object PhoneNumberUtil {

    /**
     * Normalizes any phone number format into a consistent E.164-ish key
     * so contacts stored differently (+91, 0091, spaces, dashes) still match
     * the same Firestore user document.
     */
    fun normalize(rawNumber: String, defaultCountryCode: String = "91"): String {
        var cleaned = rawNumber.filter { it.isDigit() || it == '+' }

        cleaned = cleaned.replace(Regex("^00"), "+")

        if (!cleaned.startsWith("+")) {
            cleaned = when {
                cleaned.length == 10 -> "+$defaultCountryCode$cleaned"
                cleaned.startsWith("0") && cleaned.length == 11 ->
                    "+$defaultCountryCode${cleaned.substring(1)}"
                else -> "+$cleaned"
            }
        }

        return cleaned
    }

    fun isValid(normalized: String): Boolean {
        return normalized.matches(Regex("^\\+[1-9]\\d{7,14}$"))
    }
}
