package cn.kurt6.cobblemon_ranked.battle

import cn.kurt6.cobblemon_ranked.config.ArenaCoordinate
import cn.kurt6.cobblemon_ranked.config.BattleArena
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.util.RankUtils
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID

internal fun requestArenaImpl(
    players: List<ServerPlayerEntity>,
    requiredSeats: Int,
    onArenaFound: (BattleArena, List<ArenaCoordinate>) -> Unit,
    onAbort: (ServerPlayerEntity) -> Unit
) {
    val selected = findAvailableArena(requiredSeats)
    if (selected != null) {
        BattleHandler.lockArena(selected, players)
        onArenaFound(selected, selected.playerPositions.take(requiredSeats))
        return
    }

    val request = BattleHandler.PendingBattleRequest(players, requiredSeats, onArenaFound, onAbort)
    BattleHandler.pendingRequests.add(request)
    val lang = BattleHandler.config.defaultLang
    players.forEach { player ->
        RankUtils.sendMessage(
            player,
            MessageConfig.get("queue.waiting_for_arena", lang, "position" to BattleHandler.pendingRequests.size.toString())
        )
    }
}

internal fun isPlayerWaitingForArenaImpl(uuid: UUID): Boolean {
    return BattleHandler.pendingRequests.any { request -> request.players.any { it.uuid == uuid } }
}

internal fun removePlayerFromWaitingQueueImpl(uuid: UUID) {
    val iterator = BattleHandler.pendingRequests.iterator()
    while (iterator.hasNext()) {
        val request = iterator.next()
        if (request.players.none { it.uuid == uuid }) {
            continue
        }

        iterator.remove()
        request.assignedArena?.let(BattleHandler::releaseArena)

        val lang = BattleHandler.config.defaultLang
        request.players.forEach { player ->
            if (player.uuid != uuid) {
                RankUtils.sendMessage(player, MessageConfig.get("queue.opponent_disconnected", lang))
                request.onAbort(player)
            }
        }
        return
    }
}

internal fun lockArenaImpl(arena: BattleArena, players: List<ServerPlayerEntity>) {
    BattleHandler.occupiedArenas.add(arena)
    players.forEach { player -> BattleHandler.playerToArena[player.uuid] = arena }
}

internal fun releaseArenaImpl(arena: BattleArena) {
    val shouldProcess = synchronized(BattleHandler.occupiedArenas) {
        val removed = BattleHandler.occupiedArenas.remove(arena)
        if (removed) {
            BattleHandler.logger.debug("Released arena: ${arena.world}")
        }
        removed
    }

    if (shouldProcess) {
        BattleHandler.processPendingRequests()
    }
}

internal fun releaseArenaForPlayerImpl(uuid: UUID) {
    val arena = BattleHandler.playerToArena.remove(uuid) ?: return
    BattleHandler.logger.debug("Releasing arena for player $uuid")
    BattleHandler.releaseArena(arena)
}

internal fun processPendingRequestsImpl() {
    if (BattleHandler.pendingRequests.isEmpty()) {
        return
    }

    val request = BattleHandler.pendingRequests.peek() ?: return
    if (request.players.any { it.isDisconnected }) {
        BattleHandler.pendingRequests.poll()
        request.assignedArena?.let(BattleHandler::releaseArena)
        BattleHandler.processPendingRequests()
        return
    }

    val selected = findAvailableArena(request.requiredSeats) ?: return
    val validRequest = BattleHandler.pendingRequests.poll() ?: return
    val lang = BattleHandler.config.defaultLang

    validRequest.players.forEach { player ->
        RankUtils.sendMessage(player, MessageConfig.get("queue.arena_found", lang))
    }
    BattleHandler.lockArena(selected, validRequest.players)
    validRequest.assignedArena = selected
    validRequest.onArenaFound(selected, selected.playerPositions.take(validRequest.requiredSeats))
}

private fun findAvailableArena(requiredSeats: Int): BattleArena? {
    return synchronized(BattleHandler.occupiedArenas) {
        BattleHandler.config.battleArenas
            .asSequence()
            .filter { arena -> arena.playerPositions.size >= requiredSeats && arena !in BattleHandler.occupiedArenas }
            .toList()
            .randomOrNull()
    }
}
