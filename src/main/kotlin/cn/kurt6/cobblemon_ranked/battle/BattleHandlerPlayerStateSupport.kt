package cn.kurt6.cobblemon_ranked.battle

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.data.PlayerRankData
import cn.kurt6.cobblemon_ranked.util.RankUtils
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import java.util.UUID

internal fun setReturnLocationImpl(
    uuid: UUID,
    world: ServerWorld,
    location: Triple<Double, Double, Double>
) {
    BattleHandler.returnLocations[uuid] = world to location
    BattleHandler.rankDao.saveReturnLocation(
        uuid,
        world.registryKey.value.toString(),
        location.first,
        location.second,
        location.third
    )
}

internal fun savePokemonLevelImpl(pokemonUuid: UUID, level: Int) {
    BattleHandler.pokemonOriginalLevels[pokemonUuid] = level
}

internal fun restorePokemonLevelImpl(pokemonUuid: UUID, pokemon: Pokemon) {
    val originalLevel = BattleHandler.pokemonOriginalLevels.remove(pokemonUuid) ?: return
    pokemon.level = originalLevel
}

internal fun restorePlayerPokemonLevelsImpl(player: ServerPlayerEntity) {
    Cobblemon.storage.getParty(player).forEach { pokemon ->
        BattleHandler.pokemonOriginalLevels.remove(pokemon.uuid)?.let { originalLevel ->
            pokemon.level = originalLevel
        }
    }
}

internal fun healPlayerPokemonImpl(player: ServerPlayerEntity) {
    if (!BattleHandler.config.restorePokemonHpAfterBattle) {
        return
    }

    Cobblemon.storage.getParty(player).forEach { pokemon ->
        if (!pokemon.isFainted() && pokemon.currentHealth < pokemon.maxHealth) {
            pokemon.heal()
        }
    }
}

internal fun restoreAndHealPlayersImpl(vararg players: ServerPlayerEntity) {
    players.forEach { player ->
        BattleHandler.restorePlayerPokemonLevels(player)
        BattleHandler.healPlayerPokemon(player)
    }
}

internal fun clearBattleStateImpl(vararg players: ServerPlayerEntity) {
    players.forEach { player ->
        BattleHandler.pendingTurnTimeouts.remove(player.uuid)
        BattleHandler.setPlayerInRankedBattle(player.uuid, false)
        BattleHandler.clearPlayerUsedPokemon(player.uuid)
    }
}

internal fun releaseArenaForPlayersImpl(vararg players: ServerPlayerEntity) {
    players.asSequence()
        .mapNotNull { player -> BattleHandler.playerToArena.remove(player.uuid) }
        .distinct()
        .forEach(BattleHandler::releaseArena)
}

internal fun setPlayerInRankedBattleImpl(playerId: UUID, inBattle: Boolean) {
    if (inBattle) {
        BattleHandler.playersInRankedBattle.add(playerId)
    } else {
        BattleHandler.playersInRankedBattle.remove(playerId)
    }
}

internal fun isPlayerBlockBreakingRestrictedImpl(playerId: UUID): Boolean {
    return BattleHandler.config.preventBlockBreaking && playerId in BattleHandler.playersInRankedBattle
}

internal fun cleanupStaleRankedBattleMarkersImpl(server: MinecraftServer) {
    val toRemove = BattleHandler.playersInRankedBattle.filter { playerId ->
        val player = server.playerManager.getPlayer(playerId)
        player == null || !BattleHandler.isPlayerInRankedBattle(player)
    }

    if (toRemove.isNotEmpty()) {
        BattleHandler.playersInRankedBattle.removeAll(toRemove.toSet())
        toRemove.forEach(BattleHandler.pendingTurnTimeouts::remove)
        BattleHandler.logger.debug("Cleaned up ${toRemove.size} stale ranked battle markers")
    }
}

internal fun clearAllRankedBattleMarkersImpl() {
    BattleHandler.playersInRankedBattle.clear()
    BattleHandler.pendingTurnTimeouts.clear()
}

internal fun clearPlayerUsedPokemonImpl(playerUuid: UUID) {
    BattleHandler.usedPokemonUuids.remove(playerUuid)
    BattleHandler.logger.debug("Cleared used Pokemon for player $playerUuid")
}

internal fun clearAllUsedPokemonImpl() {
    BattleHandler.usedPokemonUuids.clear()
}

internal fun cleanupStaleUsedPokemonMarkersImpl(server: MinecraftServer) {
    val toRemove = BattleHandler.usedPokemonUuids.keys.filter { playerId ->
        val player = server.playerManager.getPlayer(playerId)
        player == null || !BattleHandler.isPlayerInRankedBattle(player)
    }

    if (toRemove.isNotEmpty()) {
        toRemove.forEach(BattleHandler.usedPokemonUuids::remove)
        BattleHandler.logger.debug("Cleaned up ${toRemove.size} stale used Pokemon markers")
    }
}

internal fun grantVictoryRewardsImpl(winner: ServerPlayerEntity, server: MinecraftServer) {
    val rewards = BattleHandler.config.victoryRewards
    if (rewards.isEmpty()) {
        return
    }

    val lang = BattleHandler.config.defaultLang
    RankUtils.sendMessage(winner, MessageConfig.get("battle.VictoryRewards", lang))
    rewards.forEach { command -> BattleHandler.executeRewardCommand(command, winner, server) }
}

internal fun teleportBackIfPossibleImpl(player: PlayerEntity) {
    if (player !is ServerPlayerEntity) {
        return
    }

    val lang = BattleHandler.config.defaultLang
    var data = BattleHandler.returnLocations.remove(player.uuid)
    if (data == null) {
        val dbLocation = BattleHandler.rankDao.getReturnLocation(player.uuid)
        if (dbLocation != null) {
            val (worldId, coordinates) = dbLocation
            val identifier = Identifier.tryParse(worldId)
            if (identifier != null) {
                val worldKey = RegistryKey.of(RegistryKeys.WORLD, identifier)
                val world = player.server.getWorld(worldKey)
                if (world != null) {
                    data = world to Triple(coordinates.first, coordinates.second, coordinates.third)
                }
            }
        }
    }

    if (data != null) {
        player.teleport(data.first, data.second.first, data.second.second, data.second.third, 0f, 0f)
        RankUtils.sendMessage(player, MessageConfig.get("battle.teleport.back", lang))
        BattleHandler.rankDao.deleteReturnLocation(player.uuid)
    }
}

internal fun sendBattleResultMessageImpl(player: PlayerEntity, data: PlayerRankData, eloChange: Int) {
    val lang = BattleHandler.config.defaultLang
    val changeText = if (eloChange > 0) "§a+$eloChange" else "§c$eloChange"
    val rankTitle = data.getRankTitle()
    player.sendMessage(Text.literal(MessageConfig.get("battle.result.header", lang)))
    player.sendMessage(Text.literal(MessageConfig.get("battle.result.rank", lang, "rank" to rankTitle)))
    player.sendMessage(Text.literal(MessageConfig.get("battle.result.change", lang, "change" to changeText)))
    player.sendMessage(Text.literal(MessageConfig.get("battle.result.elo", lang, "elo" to data.elo.toString())))
    player.sendMessage(
        Text.literal(
            MessageConfig.get(
                "battle.result.record",
                lang,
                "wins" to data.wins.toString(),
                "losses" to data.losses.toString()
            )
        )
    )
}

internal fun grantRankRewardImpl(
    player: PlayerEntity,
    rank: String,
    format: String,
    server: MinecraftServer
): Boolean {
    val lang = BattleHandler.config.defaultLang
    val seasonId = BattleHandler.seasonManager.currentSeasonId
    val playerData = BattleHandler.rankDao.getPlayerData(player.uuid, seasonId, format) ?: return false
    val rewards = BattleHandler.config.rankRewards[format]?.get(rank)

    if (rewards.isNullOrEmpty()) {
        player.sendMessage(Text.literal(MessageConfig.get("reward.not_configured", lang)).formatted(Formatting.RED))
        return false
    }

    rewards.forEach { command -> BattleHandler.executeRewardCommand(command, player, server) }
    if (!playerData.hasClaimedReward(rank, format)) {
        playerData.markRewardClaimed(rank, format)
        BattleHandler.rankDao.savePlayerData(playerData)
    }
    player.sendMessage(Text.literal(MessageConfig.get("reward.granted", lang, "rank" to rank)).formatted(Formatting.GREEN))
    return true
}

internal fun executeRewardCommandImpl(command: String, player: PlayerEntity, server: MinecraftServer) {
    val formattedCommand = command
        .replace("{player}", player.name.string)
        .replace("{uuid}", player.uuid.toString())
    server.commandManager.executeWithPrefix(server.commandSource, formattedCommand)
}

internal fun recordBattlePokemonUsageImpl(battle: PokemonBattle, seasonId: Int) {
    battle.actors.filterIsInstance<PlayerBattleActor>().forEach { actor ->
        val player = actor.entity ?: return@forEach
        BattleHandler.recordSelectedPokemonUsage(player, actor.pokemonList.map { it.uuid }, seasonId)
    }
}

internal fun recordSelectedPokemonUsageImpl(
    player: ServerPlayerEntity,
    selectedPokemon: List<UUID>,
    seasonId: Int
) {
    val partyByUuid = Cobblemon.storage.getParty(player).associateBy { pokemon -> pokemon.uuid }
    selectedPokemon.forEach { pokemonUuid ->
        val pokemon = partyByUuid[pokemonUuid] ?: return@forEach
        BattleHandler.rankDao.incrementPokemonUsage(seasonId, pokemon.species.name)
    }
}

internal fun forceCleanupPlayerBattleDataImpl(player: ServerPlayerEntity) {
    BattleHandler.returnLocations.remove(player.uuid)
    BattleHandler.playerToArena.remove(player.uuid)
    BattleHandler.pendingTurnTimeouts.remove(player.uuid)
    BattleHandler.setPlayerInRankedBattle(player.uuid, false)
    BattleHandler.clearPlayerUsedPokemon(player.uuid)
}
