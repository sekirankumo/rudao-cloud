package com.eaglelan.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/** Finds the computer on the local network by UDP broadcast, so the user only has to enter the token. */
object Discovery {
    private val PROBE = "RUDAO_DISCOVER".toByteArray()

    suspend fun find(port: Int = Api.DEFAULT_PORT, timeoutMs: Long = 1500): List<String> = withContext(Dispatchers.IO) {
        val found = linkedSetOf<String>()
        runCatching {
            DatagramSocket().use { sock ->
                sock.broadcast = true
                sock.soTimeout = 300
                val targets = broadcastAddresses() + InetAddress.getByName("255.255.255.255")
                targets.distinct().forEach { addr ->
                    runCatching { sock.send(DatagramPacket(PROBE, PROBE.size, addr, port)) }
                }
                val buf = ByteArray(512)
                val end = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < end) {
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        sock.receive(packet)
                        val obj = AppJson.parseToJsonElement(String(packet.data, 0, packet.length)).jsonObject
                        if (obj["app"]?.jsonPrimitive?.content == "eagle-lan") {
                            val p = obj["port"]?.jsonPrimitive?.int ?: port
                            found += "http://${packet.address.hostAddress}:$p"
                        }
                    } catch (_: SocketTimeoutException) {
                    } catch (_: Exception) {
                        // ignore malformed replies
                    }
                }
            }
        }
        found.toList()
    }

    private fun broadcastAddresses(): List<InetAddress> =
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { nif -> nif.interfaceAddresses.filter { it.address is Inet4Address }.mapNotNull { it.broadcast } }
        }.getOrDefault(emptyList())
}

/** True for addresses on a home/office network (LAN route), false for public hosts. */
fun isPrivateUrl(url: String): Boolean {
    val host = url.substringAfter("://").substringBefore('/').substringBefore(':')
    val p = host.split('.').mapNotNull { it.toIntOrNull() }
    if (p.size != 4) return false
    return p[0] == 10 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31)
}
