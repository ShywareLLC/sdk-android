package com.sayists.shyware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BallotIdTest {
    @Test fun matchesCanonicalByteConcatenation() {
        val beacon = "ab".repeat(32)
        val nonce = (0 until 32).joinToString("") { "%02x".format(it) }
        assertEquals("b10cda1a64486b07978a11487725ee4861232b3ce87c114a78abbbb2304ea224", deriveBallotId(beacon, nonce))
        assertEquals(deriveBallotId(beacon, nonce), deriveBallotId(beacon.uppercase(), nonce.uppercase()))
    }

    @Test fun refusesMissingAndMalformedReceiptMaterial() {
        val nonce = "ab".repeat(32)
        assertThrows(IllegalArgumentException::class.java) { deriveBallotId("", nonce) }
        assertThrows(IllegalArgumentException::class.java) { deriveBallotId("zz".repeat(32), nonce) }
        assertThrows(IllegalArgumentException::class.java) { deriveBallotId(nonce, "abc") }
    }
}
