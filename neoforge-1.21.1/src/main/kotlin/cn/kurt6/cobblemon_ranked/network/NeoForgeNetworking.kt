package cn.kurt6.cobblemon_ranked.network

import cn.kurt6.cobblemon_ranked.client.network.ClientNetworking
import cn.kurt6.cobblemon_ranked.battle.TeamSelectionManager
import cn.kurt6.cobblemon_ranked.util.ClientVersionTracker
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import net.neoforged.neoforge.network.registration.NetworkRegistry

object NeoForgeNetworking {
    private const val NETWORK_VERSION = "1"

    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(NETWORK_VERSION).optional()

        registrar.playToServer(RequestPlayerRankPayload.TYPE, RequestPlayerRankPayload.CODEC) { payload, context ->
            ServerNetworking.handle(payload, context)
        }
        registrar.playToServer(ClientVersionPayload.TYPE, ClientVersionPayload.CODEC) { payload, context ->
            ClientVersionTracker.setPlayerVersion(context.player().uuid, payload.version)
        }
        registrar.playToServer(TeamSelectionSubmitPayload.TYPE, TeamSelectionSubmitPayload.CODEC) { payload, context ->
            TeamSelectionManager.handleSubmission(context.player() as ServerPlayer, payload.selectedUuids)
        }

        registrar.playToClient(PlayerRankDataPayload.TYPE, PlayerRankDataPayload.CODEC) { payload, context ->
            if (FMLEnvironment.dist.isClient) {
                context.enqueueWork { ClientNetworking.handlePlayerRank(payload) }
            }
        }
        registrar.playToClient(SeasonInfoTextPayload.TYPE, SeasonInfoTextPayload.CODEC) { payload, context ->
            if (FMLEnvironment.dist.isClient) {
                context.enqueueWork { ClientNetworking.handleSeasonInfo(payload) }
            }
        }
        registrar.playToClient(LeaderboardPayload.TYPE, LeaderboardPayload.CODEC) { payload, context ->
            if (FMLEnvironment.dist.isClient) {
                context.enqueueWork { ClientNetworking.handleLeaderboard(payload) }
            }
        }
        registrar.playToClient(TeamSelectionStartPayload.TYPE, TeamSelectionStartPayload.CODEC) { payload, context ->
            if (FMLEnvironment.dist.isClient) {
                context.enqueueWork { ClientNetworking.handleTeamSelectionStart(payload) }
            }
        }
        registrar.playToClient(TeamSelectionEndPayload.TYPE, TeamSelectionEndPayload.CODEC) { payload, context ->
            if (FMLEnvironment.dist.isClient) {
                context.enqueueWork { ClientNetworking.handleTeamSelectionEnd(payload) }
            }
        }
    }

    fun sendToPlayer(player: ServerPlayer, payload: CustomPacketPayload) {
        if (canSend(player, payload.type())) {
            PacketDistributor.sendToPlayer(player, payload)
        }
    }

    fun canSend(player: ServerPlayer, type: CustomPacketPayload.Type<out CustomPacketPayload>): Boolean {
        return NetworkRegistry.hasChannel(player.connection, type.id())
    }

    fun player(context: IPayloadContext): ServerPlayer {
        return context.player() as ServerPlayer
    }
}
