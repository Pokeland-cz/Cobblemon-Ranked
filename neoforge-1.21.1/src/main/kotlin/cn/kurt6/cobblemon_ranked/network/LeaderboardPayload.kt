package cn.kurt6.cobblemon_ranked.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

data class LeaderboardPayload(val text: String, val page: Int) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    fun write(buf: FriendlyByteBuf) {
        buf.writeUtf(text)
        buf.writeVarInt(page)
    }

    companion object {
        val TYPE = CustomPacketPayload.Type<LeaderboardPayload>(
            ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "leaderboard_data")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, LeaderboardPayload> = StreamCodec.of(
            { buf, payload -> payload.write(buf) },
            { buf -> LeaderboardPayload(buf.readUtf(), buf.readVarInt()) }
        )
    }
}

