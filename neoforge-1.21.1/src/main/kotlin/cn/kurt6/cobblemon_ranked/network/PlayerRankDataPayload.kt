package cn.kurt6.cobblemon_ranked.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

data class PlayerRankDataPayload(
    val playerName: String,
    val format: String,
    val seasonId: Int,
    val elo: Int,
    val wins: Int,
    val losses: Int,
    val winStreak: Int,
    val bestWinStreak: Int,
    val fleeCount: Int,
    val rankTitle: String,
    val globalRank: Int? // null = 未上榜
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    fun write(buf: FriendlyByteBuf) {
        buf.writeUtf(playerName)
        buf.writeUtf(format)
        buf.writeInt(seasonId)
        buf.writeInt(elo)
        buf.writeInt(wins)
        buf.writeInt(losses)
        buf.writeInt(winStreak)
        buf.writeInt(bestWinStreak)
        buf.writeInt(fleeCount)
        buf.writeUtf(rankTitle)
        buf.writeBoolean(globalRank != null)
        if (globalRank != null) buf.writeInt(globalRank)
    }

    companion object {
        val TYPE = CustomPacketPayload.Type<PlayerRankDataPayload>(
            ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "player_rank_data")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, PlayerRankDataPayload> = StreamCodec.of(
            { buf, payload -> payload.write(buf) },
            { buf -> read(buf) }
        )

        fun read(buf: FriendlyByteBuf): PlayerRankDataPayload {
            val playerName = buf.readUtf()
            val format = buf.readUtf()
            val seasonId = buf.readInt()
            val elo = buf.readInt()
            val wins = buf.readInt()
            val losses = buf.readInt()
            val winStreak = buf.readInt()
            val bestWinStreak = buf.readInt()
            val fleeCount = buf.readInt()
            val rankTitle = buf.readUtf()
            val hasRank = buf.readBoolean()
            val globalRank = if (hasRank) buf.readInt() else null

            return PlayerRankDataPayload(
                playerName, format, seasonId, elo, wins, losses,
                winStreak, bestWinStreak, fleeCount, rankTitle, globalRank
            )
        }
    }
}
