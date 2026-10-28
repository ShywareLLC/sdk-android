package com.sayists.shyware

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerReceiptTest {
    @Test fun opaqueServerIdDoesNotInventReceiptMaterial() {
        val result = ballotResultFromResponse(JSONObject().put("ballotId", "server-generated-id"))
        assertEquals("server-generated-id", result.ballotId)
        assertEquals("", result.ballotNonce)
        assertEquals("", result.beaconBlockHash)
        assertEquals("", result.identityHash)
        assertEquals("", result.txJson)
        assertThrows(IllegalArgumentException::class.java) { ballotResultFromResponse(JSONObject()) }
    }

    @Test fun serverReceiptMustMatchItsCanonicalId() {
        val beacon = "ab".repeat(32)
        val nonce = (0 until 32).joinToString("") { "%02x".format(it) }
        val response = JSONObject().put("ballotId", deriveBallotId(beacon, nonce))
            .put("ballotNonce", nonce).put("beaconBlockHash", beacon)
        assertEquals(nonce, ballotResultFromResponse(response).ballotNonce)
        response.put("ballotId", "unrelated-id")
        assertThrows(IllegalArgumentException::class.java) { ballotResultFromResponse(response) }
        response.remove("beaconBlockHash")
        assertThrows(IllegalArgumentException::class.java) { ballotResultFromResponse(response) }
    }
}
