package cn.kurt6.cobblemon_ranked.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import java.util.UUID

data class SelectionPokemonInfo(
    val uuid: UUID,
    val species: String,
    val displayName: String,
    val level: Int,
    val gender: String,
    val shiny: Boolean,
    val form: String
) {
    companion object {
        fun write(buf: FriendlyByteBuf, info: SelectionPokemonInfo) {
            buf.writeUUID(info.uuid)
            buf.writeUtf(info.species)
            buf.writeUtf(info.displayName)
            buf.writeInt(info.level)
            buf.writeUtf(info.gender)
            buf.writeBoolean(info.shiny)
            buf.writeUtf(info.form)
        }

        fun read(buf: FriendlyByteBuf): SelectionPokemonInfo {
            return SelectionPokemonInfo(
                buf.readUUID(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readUtf()
            )
        }
    }
}

data class TeamSelectionStartPayload(
    val limit: Int,
    val timeLimitSeconds: Int,
    val opponentName: String,
    val opponentTeam: List<SelectionPokemonInfo>,
    val yourTeam: List<SelectionPokemonInfo>
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<TeamSelectionStartPayload>(ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "team_selection_start"))

        val CODEC: StreamCodec<FriendlyByteBuf, TeamSelectionStartPayload> = StreamCodec.of(
            { buf, payload ->
                buf.writeVarInt(payload.limit)
                buf.writeVarInt(payload.timeLimitSeconds)
                buf.writeUtf(payload.opponentName)

                buf.writeVarInt(payload.opponentTeam.size)
                payload.opponentTeam.forEach { SelectionPokemonInfo.write(buf, it) }

                buf.writeVarInt(payload.yourTeam.size)
                payload.yourTeam.forEach { SelectionPokemonInfo.write(buf, it) }
            },
            { buf ->
                val limit = buf.readVarInt()
                val time = buf.readVarInt()
                val opName = buf.readUtf()

                val opCount = buf.readVarInt()
                val opTeam = List(opCount) { SelectionPokemonInfo.read(buf) }

                val myCount = buf.readVarInt()
                val myTeam = List(myCount) { SelectionPokemonInfo.read(buf) }

                TeamSelectionStartPayload(limit, time, opName, opTeam, myTeam)
            }
        )
    }
}

data class TeamSelectionSubmitPayload(
    val selectedUuids: List<UUID>
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<TeamSelectionSubmitPayload>(ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "team_selection_submit"))

        val CODEC: StreamCodec<FriendlyByteBuf, TeamSelectionSubmitPayload> = StreamCodec.of(
            { buf, payload ->
                buf.writeVarInt(payload.selectedUuids.size)
                payload.selectedUuids.forEach { buf.writeUUID(it) }
            },
            { buf ->
                val count = buf.readVarInt()
                val uuids = List(count) { buf.readUUID() }
                TeamSelectionSubmitPayload(uuids)
            }
        )
    }
}

class TeamSelectionEndPayload private constructor() : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val INSTANCE = TeamSelectionEndPayload()
        val TYPE = CustomPacketPayload.Type<TeamSelectionEndPayload>(ResourceLocation.fromNamespaceAndPath("cobblemon_ranked", "team_selection_end"))
        val CODEC: StreamCodec<FriendlyByteBuf, TeamSelectionEndPayload> = StreamCodec.unit(INSTANCE)
    }
}
