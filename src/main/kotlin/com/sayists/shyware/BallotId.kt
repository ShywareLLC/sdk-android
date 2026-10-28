package com.sayists.shyware

import java.security.MessageDigest

internal fun deriveBallotId(beaconBlockHash: String, nonce: String): String {
    fun decode(value: String): ByteArray {
        require(value.length == 64 && value.all { it in "0123456789abcdefABCDEF" }) {
            "A 32-byte hexadecimal beacon and nonce are required for receipt verification"
        }
        return ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
    val bytes = decode(beaconBlockHash) + decode(nonce)
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
