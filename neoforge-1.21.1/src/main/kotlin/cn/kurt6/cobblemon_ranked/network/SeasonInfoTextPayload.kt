package cn.kurt6.cobblemon_ranked.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

data class SeasonInfoTextPayload(val text: String) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    fun write(buf: FriendlyByteBuf) {
        buf.writeUtf(text)
    }

    companion object {
        val TYPE = CustomPacketPayload.Type<SeasonInfoTextPayload>(
            ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "season_info_text")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, SeasonInfoTextPayload> = StreamCodec.of(
            { buf: FriendlyByteBuf, payload: SeasonInfoTextPayload -> payload.write(buf) },
            { buf: FriendlyByteBuf -> read(buf) }
        )

        fun read(buf: FriendlyByteBuf): SeasonInfoTextPayload {
            return SeasonInfoTextPayload(buf.readUtf())
        }
    }
}
