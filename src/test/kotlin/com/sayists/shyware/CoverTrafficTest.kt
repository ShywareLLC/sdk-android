package com.sayists.shyware

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class CoverTrafficTest {
    @Test fun fixedEnvelopeUsesUtf8Bytes() {
        for (kind in listOf("dummy", "cast", "update")) {
            val bytes = CoverTrafficAdapter.encode(kind, Json.parseToJsonElement("{\"tx\":\"é\"}"))
            assertEquals(32768, bytes.size)
            assertEquals(kind, Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject["kind"]!!.jsonPrimitive.content)
        }
    }

    @Test fun realOperationsWaitForScheduledSlots() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val observed = java.util.concurrent.CopyOnWriteArrayList<String>()
        val cover = CoverTrafficAdapter(0.01, scope) { bytes ->
            observed.add(Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject["kind"]!!.jsonPrimitive.content)
            byteArrayOf(1)
        }
        try {
            cover.start()
            cover.tick()
            val pending = async { cover.submit("cast", Json.parseToJsonElement("{}")) }
            delay(10)
            assertEquals(listOf("dummy"), observed.toList())
            cover.tick()
            assertArrayEquals(byteArrayOf(1), pending.await())
            cover.tick()
            assertEquals(listOf("dummy", "cast", "dummy"), observed.toList())
        } finally { cover.stop(); scope.cancel() }
    }
}
