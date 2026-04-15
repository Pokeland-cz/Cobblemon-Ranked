package cn.kurt6.cobblemon_ranked.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

enum class RequestType {
    PLAYER,
    SEASON,
    LEADERBOARD
}

data class RequestPlayerRankPayload(
    val type: RequestType,
    val format: String = "singles",
    val extra: String? = null  // 可选参数：页码 / 其他扩展
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    fun write(buf: FriendlyByteBuf) {
        buf.writeEnum(type)
        buf.writeUtf(format)
        buf.writeBoolean(extra != null)
        if (extra != null) {
            buf.writeUtf(extra)
        }
    }

    companion object {
        val TYPE = CustomPacketPayload.Type<RequestPlayerRankPayload>(
            ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "request_player_rank")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, RequestPlayerRankPayload> =
            StreamCodec.of<FriendlyByteBuf, RequestPlayerRankPayload>(
                { buf: FriendlyByteBuf, payload: RequestPlayerRankPayload -> payload.write(buf) },
                { buf: FriendlyByteBuf -> read(buf) }
            )

        fun read(buf: FriendlyByteBuf): RequestPlayerRankPayload {
            val type = buf.readEnum(RequestType::class.java)
            val format = buf.readUtf()
            val hasExtra = buf.readBoolean()
            val extra = if (hasExtra) buf.readUtf() else null
            return RequestPlayerRankPayload(type, format, extra)
        }
    }
}
