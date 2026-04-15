package cn.kurt6.cobblemon_ranked.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

data class ClientVersionPayload(
    val version: String
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<ClientVersionPayload>(ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "client_version"))
        const val MINIMUM_VERSION = "1.4.1"

        val CODEC: StreamCodec<FriendlyByteBuf, ClientVersionPayload> = StreamCodec.of(
            { buf, payload ->
                buf.writeUtf(payload.version)
            },
            { buf ->
                ClientVersionPayload(buf.readUtf())
            }
        )

        fun isVersionCompatible(version: String): Boolean {
            return try {
                val clientParts = version.split(".").map { it.toIntOrNull() ?: 0 }
                val minParts = MINIMUM_VERSION.split(".").map { it.toInt() }

                for (i in 0 until minOf(clientParts.size, minParts.size)) {
                    when {
                        clientParts[i] > minParts[i] -> return true
                        clientParts[i] < minParts[i] -> return false
                    }
                }
                clientParts.size >= minParts.size
            } catch (e: Exception) {
                false
            }
        }
    }
}
