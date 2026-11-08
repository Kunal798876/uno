package com.example.uno.network

import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object RoomNetworkManager {
    private const val BASE_URL = "https://ntfy.sh/unoroyale_room_"

    /**
     * Broadcast an event to the shared room channel
     */
    suspend fun postMessage(roomCode: String, jsonPayload: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL$roomCode")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(jsonPayload)
                writer.flush()
            }

            val responseCode = conn.responseCode
            conn.disconnect()
            responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Poll recent messages on room channel
     */
    suspend fun pollMessages(roomCode: String, sinceUnixSec: Long = 0): List<JSONObject> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<JSONObject>()
        try {
            val query = if (sinceUnixSec > 0) "?poll=1&since=${sinceUnixSec}" else "?poll=1"
            val url = URL("$BASE_URL$roomCode/json$query")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
            }

            if (conn.responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { reader ->
                    var line: String? = null
                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line?.trim().orEmpty()
                        if (trimmed.isNotEmpty()) {
                            try {
                                val envelope = JSONObject(trimmed)
                                if (envelope.optString("event") == "message") {
                                    val msgStr = envelope.optString("message")
                                    if (msgStr.isNotEmpty()) {
                                        messages.add(JSONObject(msgStr))
                                    }
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            }
            conn.disconnect()
        } catch (_: Exception) {
        }
        messages
    }

    /**
     * Persistent HTTP stream for real-time delivery without polling delay.
     * ntfy.sh unblocks immediately as soon as any device publishes a message.
     */
    fun startStreaming(
        scope: CoroutineScope,
        roomCode: String,
        onMessageReceived: (JSONObject) -> Unit
    ): Job {
        return scope.launch(Dispatchers.IO) {
            val seenMessageIds = mutableSetOf<String>()

            while (isActive) {
                var conn: HttpURLConnection? = null
                try {
                    val url = URL("$BASE_URL$roomCode/json")
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 5000
                        readTimeout = 65000 // ntfy sends heartbeats every 45s
                    }

                    if (conn.responseCode in 200..299) {
                        val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                        var line: String? = null
                        while (isActive && reader.readLine().also { line = it } != null) {
                            val trimmed = line?.trim().orEmpty()
                            if (trimmed.isNotEmpty()) {
                                try {
                                    val envelope = JSONObject(trimmed)
                                    val event = envelope.optString("event")
                                    if (event == "message") {
                                        val id = envelope.optString("id")
                                        if (id.isEmpty() || !seenMessageIds.contains(id)) {
                                            if (id.isNotEmpty()) seenMessageIds.add(id)
                                            val msgStr = envelope.optString("message")
                                            if (msgStr.isNotEmpty()) {
                                                val msgJson = JSONObject(msgStr)
                                                withContext(Dispatchers.Main) {
                                                    onMessageReceived(msgJson)
                                                }
                                            }
                                        }
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Quick backoff before reconnecting on timeout / connection drop
                    delay(250)
                } finally {
                    try {
                        conn?.disconnect()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }
}
