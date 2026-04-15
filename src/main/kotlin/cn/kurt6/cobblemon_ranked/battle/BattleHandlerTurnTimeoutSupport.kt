package cn.kurt6.cobblemon_ranked.battle

import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.util.RankUtils
import com.cobblemon.mod.common.battles.ActiveBattlePokemon
import com.cobblemon.mod.common.battles.ForcePassActionResponse
import com.cobblemon.mod.common.battles.MoveActionResponse
import com.cobblemon.mod.common.battles.PassActionResponse
import com.cobblemon.mod.common.battles.ShowdownActionResponse
import com.cobblemon.mod.common.battles.ShowdownMoveset
import com.cobblemon.mod.common.battles.SwitchActionResponse
import com.cobblemon.mod.common.battles.Targetable
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID

internal fun processTurnTimeoutsImpl(server: MinecraftServer) {
    val timeoutSeconds = BattleHandler.config.turnActionTimeoutSeconds
    if (timeoutSeconds <= 0) {
        BattleHandler.pendingTurnTimeouts.clear()
        return
    }

    val now = System.currentTimeMillis()
    val timeoutMillis = timeoutSeconds * 1000L
    val activeTimeoutPlayers = mutableSetOf<UUID>()

    for ((battle, battleId) in BattleHandler.battleToIdMap) {
        if (battle.ended || BattleHandler.rankedBattles[battleId] == null) {
            continue
        }

        for (actor in battle.actors.filterIsInstance<PlayerBattleActor>()) {
            val player = actor.entity ?: server.playerManager.getPlayer(actor.uuid)
            if (player == null || player.isDisconnected || !actor.mustChoose) {
                BattleHandler.pendingTurnTimeouts.remove(actor.uuid)
                continue
            }

            val request = actor.request
            if (request == null || request.wait) {
                BattleHandler.pendingTurnTimeouts.remove(actor.uuid)
                continue
            }

            activeTimeoutPlayers += actor.uuid

            val currentState = BattleHandler.pendingTurnTimeouts[actor.uuid]
            if (currentState == null || currentState.battleId != battleId || currentState.turn != battle.turn) {
                BattleHandler.pendingTurnTimeouts[actor.uuid] = BattleHandler.PendingTurnTimeout(battleId, battle.turn, now)
                RankUtils.sendMessage(
                    player,
                    MessageConfig.get("battle.turn.start", BattleHandler.config.defaultLang, "seconds" to timeoutSeconds.toString())
                )
                continue
            }

            val elapsedMillis = now - currentState.startedAtMillis
            val remainingSeconds = ((timeoutMillis - elapsedMillis + 999) / 1000).coerceAtLeast(0).toInt()

            if (remainingSeconds != currentState.lastDisplayedRemainingSeconds) {
                currentState.lastDisplayedRemainingSeconds = remainingSeconds
                RankUtils.sendActionBar(
                    player,
                    MessageConfig.get(
                        "battle.turn.remaining_actionbar",
                        BattleHandler.config.defaultLang,
                        "seconds" to remainingSeconds.toString()
                    )
                )
            }

            if (elapsedMillis < timeoutMillis) {
                continue
            }

            val handled = autoChooseActionImpl(player, actor, battleId)
            if (handled) {
                BattleHandler.pendingTurnTimeouts.remove(actor.uuid)
            } else {
                BattleHandler.pendingTurnTimeouts[actor.uuid] = currentState.copy(startedAtMillis = now)
            }
        }
    }

    BattleHandler.pendingTurnTimeouts.keys.removeIf { it !in activeTimeoutPlayers }
}

private fun autoChooseActionImpl(player: ServerPlayerEntity, actor: PlayerBattleActor, battleId: UUID): Boolean {
    val responses = buildAutomaticResponsesImpl(actor) ?: return false

    return runCatching {
        actor.setActionResponses(responses)
        RankUtils.sendMessage(player, MessageConfig.get("battle.turn.timeout_auto_action", BattleHandler.config.defaultLang))
        BattleHandler.logger.info(
            "Auto selected actions for player {} in ranked battle {}: {}",
            player.name.string,
            battleId,
            responses.joinToString(", ")
        )
        true
    }.getOrElse { error ->
        BattleHandler.logger.warn("Failed to auto select action for player ${player.name.string} in battle $battleId", error)
        false
    }
}

private fun buildAutomaticResponsesImpl(actor: PlayerBattleActor): List<ShowdownActionResponse>? {
    val request = actor.request ?: return null
    val activePokemon = actor.activePokemon
    if (activePokemon.isEmpty()) {
        return null
    }

    val reservedSwitches = mutableSetOf<UUID>()
    val responses = mutableListOf<ShowdownActionResponse>()

    activePokemon.forEachIndexed { index, active ->
        val moveset = request.active?.getOrNull(index)
        val forceSwitch = request.forceSwitch.getOrNull(index) == true

        val response = when {
            forceSwitch -> selectAutomaticSwitchImpl(actor, active, moveset, reservedSwitches) ?: PassActionResponse
            else -> selectAutomaticMoveImpl(active, moveset)
                ?: selectAutomaticSwitchImpl(actor, active, moveset, reservedSwitches)
                ?: if (actor.expectingPassActions.isNotEmpty()) ForcePassActionResponse() else PassActionResponse
        }

        responses += response
    }

    return responses
}

private fun selectAutomaticMoveImpl(
    activePokemon: ActiveBattlePokemon,
    moveset: ShowdownMoveset?
): MoveActionResponse? {
    if (moveset == null) {
        return null
    }

    val move = moveset.moves.firstOrNull { it.mustBeUsed() }
        ?: moveset.moves.firstOrNull { it.canBeUsed() || (it.gimmickMove?.disabled == false) }
        ?: return null

    val targetPnx = resolveAutoTargetImpl(move.getTargets(activePokemon))
    return MoveActionResponse(move.id, targetPnx, null)
}

private fun selectAutomaticSwitchImpl(
    actor: PlayerBattleActor,
    activePokemon: ActiveBattlePokemon,
    moveset: ShowdownMoveset?,
    reservedSwitches: MutableSet<UUID>
): SwitchActionResponse? {
    val activePokemonIds = actor.activePokemon.mapNotNull { active -> active.battlePokemon?.uuid }.toSet()

    return actor.pokemonList.asSequence()
        .filter { candidate ->
            candidate.uuid !in activePokemonIds &&
                candidate.uuid !in reservedSwitches &&
                candidate.health > 0
        }
        .map { candidate -> SwitchActionResponse(candidate.uuid) }
        .firstOrNull { response ->
            val isValid = response.isValid(activePokemon, moveset, true)
            if (isValid) {
                reservedSwitches += response.newPokemonId
            }
            isValid
        }
}

private fun resolveAutoTargetImpl(targets: List<Targetable>?): String? {
    return (targets?.firstOrNull() as? ActiveBattlePokemon)?.getPNX()
}
