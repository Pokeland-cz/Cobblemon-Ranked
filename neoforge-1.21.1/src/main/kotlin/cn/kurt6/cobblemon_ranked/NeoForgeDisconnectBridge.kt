package cn.kurt6.cobblemon_ranked

import cn.kurt6.cobblemon_ranked.battle.BattleHandler
import cn.kurt6.cobblemon_ranked.matchmaking.MatchmakingQueue
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object NeoForgeDisconnectBridge {
    private val handledDisconnects = ConcurrentHashMap.newKeySet<UUID>()

    fun handleDisconnect(player: ServerPlayer) {
        if (!handledDisconnects.add(player.uuid)) {
            return
        }

        BattleHandler.handlePlayerDisconnect(player, player.server)
        CobblemonRanked.INSTANCE.handlePlayerDisconnect(player)
        MatchmakingQueue.handlePlayerDisconnect(player)
    }

    fun clearDisconnectState(player: ServerPlayer) {
        handledDisconnects.remove(player.uuid)
    }
}
