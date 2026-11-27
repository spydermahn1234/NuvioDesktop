// FORK (Spyder): Nick's shelf home, not in UmbraProjects. See CLAUDE.md.
package com.nuvio.app.features.home.shelves

import co.touchlab.kermit.Logger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/** fork:shelf-home v3 phase 7 - the art lights' UDP to WiZ (38899) and Govee LAN (4003, answers on 4002). */
internal actual object ShelfArtLightsWire {
    private val log = Logger.withTag("ShelfArtLights")
    private var socket: DatagramSocket? = null

    @Synchronized
    actual fun send(address: String, port: Int, body: String) {
        runCatching {
            val s = socket ?: DatagramSocket().also { socket = it }
            val bytes = body.toByteArray()
            s.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(address), port))
        }.onFailure { log.w { "send to $address:$port failed: ${it.message}" } }
    }

    actual fun ask(address: String, port: Int, body: String, replyPort: Int?, timeoutMs: Int): String? =
        runCatching { askOnce(address, port, body, replyPort, timeoutMs) }
            .onFailure { log.w { "ask $address:$port failed: ${it.message}" } }
            .getOrNull()

    private fun askOnce(address: String, port: Int, body: String, replyPort: Int?, timeoutMs: Int): String? {
        val target = InetAddress.getByName(address)
        val bytes = body.toByteArray()
        val listen = if (replyPort == null) {
            DatagramSocket()
        } else {
            // Govee answers on a fixed port; the beat lights' scan may hold it too, so share it.
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(replyPort))
            }
        }
        listen.use { s ->
            val until = System.nanoTime() + timeoutMs * 1_000_000L
            if (replyPort == null) {
                s.send(DatagramPacket(bytes, bytes.size, target, port))
            } else {
                DatagramSocket().use { out -> out.send(DatagramPacket(bytes, bytes.size, target, port)) }
            }
            val buffer = ByteArray(2048)
            var left = timeoutMs
            while (left > 0) {
                s.soTimeout = left
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    s.receive(packet)
                } catch (_: SocketTimeoutException) {
                    return null
                }
                // Only the light we asked (a Govee port can hear other lights' answers).
                if (packet.address == target) return String(packet.data, 0, packet.length)
                left = ((until - System.nanoTime()) / 1_000_000L).toInt()
            }
            return null
        }
    }
}
