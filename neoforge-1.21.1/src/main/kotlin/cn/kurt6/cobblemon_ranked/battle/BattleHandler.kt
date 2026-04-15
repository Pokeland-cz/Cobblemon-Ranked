package cn.kurt6.cobblemon_ranked.battle

import cn.kurt6.cobblemon_ranked.*
import cn.kurt6.cobblemon_ranked.config.ArenaCoordinate
import cn.kurt6.cobblemon_ranked.config.BattleArena
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.util.*
import cn.kurt6.cobblemon_ranked.data.PlayerRankData
import cn.kurt6.cobblemon_ranked.util.PokemonUsageValidator
import cn.kurt6.cobblemon_ranked.util.RankUtils
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.world.entity.player.Player
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.event.level.BlockEvent
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

object BattleHandler {
    internal val logger = LoggerFactory.getLogger(BattleHandler::class.java)
    private const val DISCONNECT_SETTLEMENT_DELAY_MILLIS = 1500L
    private const val DISCONNECT_SETTLEMENT_RETRY_DELAY_MILLIS = 500L
    private const val DISCONNECT_SETTLEMENT_MAX_RETRIES = 6

    internal val rankedBattles = ConcurrentHashMap<UUID, String>()
    internal val battleToIdMap = ConcurrentHashMap<PokemonBattle, UUID>()

    internal val occupiedArenas = ConcurrentHashMap.newKeySet<BattleArena>()
    private val battleIdToArena = ConcurrentHashMap<UUID, BattleArena>()
    val playerToArena = ConcurrentHashMap<UUID, BattleArena>()

    internal val playersInRankedBattle = ConcurrentHashMap.newKeySet<UUID>()

    internal val pokemonOriginalLevels = ConcurrentHashMap<UUID, Int>()

    internal data class PendingBattleRequest(
        val players: List<ServerPlayer>,
        val requiredSeats: Int,
        val onArenaFound: (BattleArena, List<ArenaCoordinate>) -> Unit,
        val onAbort: (ServerPlayer) -> Unit,
        var assignedArena: BattleArena? = null
    )
    internal val pendingRequests = ConcurrentLinkedQueue<PendingBattleRequest>()
    internal data class PendingServerAction(
        val executeAtMillis: Long,
        val action: (MinecraftServer) -> Unit
    )
    internal val pendingServerActions = ConcurrentLinkedQueue<PendingServerAction>()

    internal val config get() = CobblemonRanked.config
    val rankDao get() = CobblemonRanked.rankDao
    val rewardManager get() = CobblemonRanked.rewardManager
    internal val seasonManager get() = CobblemonRanked.seasonManager
    internal val usedPokemonUuids = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    internal val returnLocations = ConcurrentHashMap<UUID, Pair<ServerLevel, Triple<Double, Double, Double>>>()
    internal val pendingTurnTimeouts = ConcurrentHashMap<UUID, PendingTurnTimeout>()

    internal data class PendingTurnTimeout(
        val battleId: UUID,
        val turn: Int,
        val startedAtMillis: Long,
        var lastDisplayedRemainingSeconds: Int = -1
    )

    fun requestArena(
        players: List<ServerPlayer>,
        requiredSeats: Int,
        onArenaFound: (BattleArena, List<ArenaCoordinate>) -> Unit,
        onAbort: (ServerPlayer) -> Unit
    ) = requestArenaImpl(players, requiredSeats, onArenaFound, onAbort)

    fun isPlayerWaitingForArena(uuid: UUID): Boolean = isPlayerWaitingForArenaImpl(uuid)

    fun removePlayerFromWaitingQueue(uuid: UUID) = removePlayerFromWaitingQueueImpl(uuid)

    internal fun lockArena(arena: BattleArena, players: List<ServerPlayer>) = lockArenaImpl(arena, players)

    fun releaseArena(arena: BattleArena) = releaseArenaImpl(arena)

    fun releaseArenaForPlayer(uuid: UUID) = releaseArenaForPlayerImpl(uuid)

    internal fun processPendingRequests() = processPendingRequestsImpl()

    internal fun scheduleServerAction(delayMillis: Long, action: (MinecraftServer) -> Unit) =
        scheduleServerActionImpl(delayMillis, action)

    fun setReturnLocation(uuid: UUID, world: ServerLevel, location: Triple<Double, Double, Double>) =
        setReturnLocationImpl(uuid, world, location)

    private fun cleanupBattleData(battle: PokemonBattle) {
        val battleId = battleToIdMap.remove(battle)
        if (battleId != null) {
            rankedBattles.remove(battleId)
        }
    }

    private fun finalBattleCleanup(battle: PokemonBattle, battleId: UUID?) {
        try {
            var arena: BattleArena? = null
            if (battleId != null) {
                val format = rankedBattles[battleId]
                if (format != "2v2singles") {
                    arena = battleIdToArena.remove(battleId)
                }
                rankedBattles.remove(battleId)
            }
            battleToIdMap.entries.removeIf { it.key == battle || it.value == battleId }

            if (arena != null) {
                releaseArena(arena)
            }
        } catch (e: Exception) {
            logger.error("Error during final battle cleanup", e)
        }
    }

    fun validateTeam(player: ServerPlayer, teamUuids: List<UUID>, format: BattleFormat): Boolean {
        val lang = CobblemonRanked.config.defaultLang
        val party = Cobblemon.storage.getParty(player)
        val partyUuids = party.map { it.uuid }.toSet()

        if (!teamUuids.all { it in partyUuids }) {
            RankUtils.sendMessage(player, MessageConfig.get("battle.invalid_team_selection", lang))
            return false
        }

        val validPokemon = teamUuids.mapNotNull { uuid -> party.find { it.uuid == uuid } }

        if (validPokemon.size != teamUuids.size) {
            RankUtils.sendMessage(player, MessageConfig.get("battle.invalid_team_selection", lang))
            return false
        }

        val pokemonList = validPokemon
        val config = CobblemonRanked.config

        if (format == BattleFormat.GEN_9_DOUBLES && pokemonList.size < 2) {
            RankUtils.sendMessage(player, MessageConfig.get("battle.team.too_small", lang, "min" to "2"))
            return false
        }

        if (pokemonList.size < config.minTeamSize) {
            RankUtils.sendMessage(player, MessageConfig.get("battle.team.too_small", lang, "min" to config.minTeamSize.toString()))
            return false
        }
        if (pokemonList.size > config.maxTeamSize) {
            RankUtils.sendMessage(player, MessageConfig.get("battle.team.too_large", lang, "max" to config.maxTeamSize.toString()))
            return false
        }

        val violations = mutableListOf<String>()
        val speciesCount = mutableMapOf<String, Int>()
        val restrictedCount = mutableMapOf<String, Int>()
        val heldItems = mutableListOf<String>()
        val seasonId = CobblemonRanked.seasonManager.currentSeasonId
        val bannedPokemon = config.bannedPokemon.asSequence().map { it.lowercase() }.toSet()
        val restrictedPokemon = config.restrictedPokemon.asSequence().map { it.lowercase() }.toSet()
        val bannedHeldItems = config.bannedHeldItems.asSequence().map { it.lowercase() }.toSet()
        val bannedNatures = config.bannedNatures.asSequence().map { it.lowercase() }.toSet()
        val bannedAbilities = config.bannedAbilities.asSequence().map { it.uppercase() }.toSet()
        val bannedGenders = config.bannedGenders.asSequence().map { it.uppercase() }.toSet()
        val bannedMoves = config.bannedMoves.asSequence().map { it.lowercase().trim() }.toSet()
        val bannedItems = config.bannedCarriedItems.asSequence().map { it.lowercase() }.toSet()
        val usageStats = if (config.banUsageBelow > 0.0 || config.banUsageAbove > 0.0 || config.banTopUsed > 0) {
            CobblemonRanked.rankDao.getUsageStatistics(seasonId)
        } else {
            emptyMap()
        }
        val totalUsage = usageStats.values.sum()

        pokemonList.forEach { pokemon ->
            val speciesName = pokemon.species.name.lowercase()

            if (speciesName in bannedPokemon) {
                violations.add("banned_pokemon:${pokemon.species.name}")
            }

            if (speciesName in restrictedPokemon) {
                restrictedCount["restricted"] = restrictedCount.getOrDefault("restricted", 0) + 1
            }

            if (config.maxLevel > 0 && pokemon.level > config.maxLevel) {
                violations.add("overlevel:${pokemon.species.name}(Lv.${pokemon.level})")
            }

            if (!config.allowDuplicateSpecies) {
                speciesCount[pokemon.species.name] = speciesCount.getOrDefault(pokemon.species.name, 0) + 1
            }

            if (!config.allowDuplicateItems) {
                val heldItem = pokemon.heldItem()
                if (!heldItem.isEmpty) {
                    heldItems.add(BuiltInRegistries.ITEM.getKey(heldItem.item).toString())
                }
            }

            if (isEgg(pokemon)) {
                violations.add("egg:${pokemon.species.name}")
            } else if (isFainted(pokemon)) {
                violations.add("fainted:${pokemon.species.name}")
            }

            val stack = pokemon.heldItem()
            if (!stack.isEmpty) {
                val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString().lowercase()
                if (itemId in bannedHeldItems) {
                    violations.add("banned_held:${pokemon.species.name}($itemId)")
                }
            }

            if (pokemon.nature.name.toString().lowercase() in bannedNatures) {
                violations.add("banned_nature:${pokemon.species.name}(${pokemon.nature.name})")
            }

            if (pokemon.ability.name.uppercase() in bannedAbilities) {
                violations.add("banned_ability:${pokemon.species.name}(${pokemon.ability.name})")
            }

            if (pokemon.gender.name.uppercase() in bannedGenders) {
                violations.add("banned_gender:${pokemon.species.name}(${pokemon.gender.name})")
            }

            val pokemonBannedMoves = pokemon.moveSet.getMovesWithNulls()
                .mapNotNull { move ->
                    val moveName = move?.name?.lowercase()
                    if (moveName in bannedMoves) moveName else null
                }
            if (pokemonBannedMoves.isNotEmpty()) {
                violations.add("banned_moves:${pokemon.species.name}(${pokemonBannedMoves.joinToString(",")})")
            }

            if (config.bannedShiny && pokemon.shiny) {
                violations.add("shiny:${pokemon.species.name}")
            }

            val usageResult = PokemonUsageValidator.validateUsageRestrictions(
                pokemon = pokemon,
                seasonId = seasonId,
                lang = lang,
                usageStats = usageStats,
                totalUsage = totalUsage
            )
            if (!usageResult.isValid) {
                usageResult.errorMessage?.let { violations.add(it) }
            }
        }

        val restrictedTotal = restrictedCount.getOrDefault("restricted", 0)
        if (restrictedTotal > config.maxRestrictedCount) {
            val restrictedNames = pokemonList
                .filter { it.species.name.lowercase() in restrictedPokemon }
                .joinToString(", ") { it.species.name }
            violations.add("restricted_exceed:${config.maxRestrictedCount}($restrictedNames)")
        }

        if (!config.allowDuplicateSpecies) {
            speciesCount.filter { it.value > 1 }.keys.forEach { species ->
                violations.add("duplicate_species:$species")
            }
        }

        if (!config.allowDuplicateItems) {
            val duplicateItems = heldItems.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
            if (duplicateItems.isNotEmpty()) {
                violations.add("duplicate_items:${duplicateItems.joinToString(",")}")
            }
        }

        val inventory = player.inventory
        val violatedItems = inventory.items
            .filterNot { it.isEmpty }
            .map { BuiltInRegistries.ITEM.getKey(it.item).toString().lowercase() }
            .filter { it in bannedItems }

        if (violatedItems.isNotEmpty()) {
            violations.add("player_banned_items:${violatedItems.joinToString(",")}")
        }

        if (violations.isNotEmpty()) {
            violations.forEach { violation ->
                val parts = violation.split(":", limit = 2)
                val type = parts[0]
                val detail = parts.getOrNull(1) ?: ""

                when (type) {
                    "banned_pokemon" -> RankUtils.sendMessage(player, MessageConfig.get("battle.team.banned_pokemon", lang, "names" to detail))
                    "restricted_exceed" -> RankUtils.sendMessage(player, MessageConfig.get("battle.team.restricted_exceed", lang, "max" to config.maxRestrictedCount.toString(), "names" to detail))
                    "overlevel" -> RankUtils.sendMessage(player, MessageConfig.get("battle.team.overleveled", lang, "max" to config.maxLevel.toString(), "names" to detail))
                    "duplicate_species" -> RankUtils.sendMessage(player, MessageConfig.get("battle.team.duplicates", lang, "names" to detail))
                    "duplicate_items" -> RankUtils.sendMessage(player, MessageConfig.get("battle.team.duplicate_items", lang))
                    "egg", "fainted" -> {
                        val status = if (type == "egg") MessageConfig.get("battle.status.egg", lang) else MessageConfig.get("battle.status.fainted", lang)
                        RankUtils.sendMessage(player, MessageConfig.get("battle.team.invalid", lang, "entries" to "$detail($status)"))
                    }
                    "banned_held" -> RankUtils.sendMessage(player, MessageConfig.get("battle.team.banned_held_items", lang, "names" to detail))
                    "banned_nature" -> RankUtils.sendMessage(player, MessageConfig.get("battle.team.banned_nature", lang, "names" to detail))
                    "banned_ability" -> RankUtils.sendMessage(player, MessageConfig.get("battle.team.banned_ability", lang, "names" to detail))
                    "banned_gender" -> RankUtils.sendMessage(player, MessageConfig.get("battle.team.banned_gender", lang, "names" to detail))
                    "banned_moves" -> RankUtils.sendMessage(player, MessageConfig.get("battle.team.banned_moves", lang, "names" to detail))
                    "shiny" -> RankUtils.sendMessage(player, MessageConfig.get("battle.team.banned_shiny", lang, "names" to detail))
                    "player_banned_items" -> RankUtils.sendMessage(player, MessageConfig.get("battle.player.banned_items", lang, "items" to detail))
                    else -> RankUtils.sendMessage(player, detail)
                }
            }
            return false
        }

        return true
    }

    private fun isEgg(pokemon: Pokemon): Boolean = pokemon.state.name.equals("egg", ignoreCase = true)
    private fun isFainted(pokemon: Pokemon): Boolean = pokemon.currentHealth <= 0 || pokemon.isFainted()

    fun savePokemonLevel(pokemonUuid: UUID, level: Int) = savePokemonLevelImpl(pokemonUuid, level)

    fun restorePokemonLevel(pokemonUuid: UUID, pokemon: com.cobblemon.mod.common.pokemon.Pokemon) =
        restorePokemonLevelImpl(pokemonUuid, pokemon)

    fun restorePlayerPokemonLevels(player: ServerPlayer) = restorePlayerPokemonLevelsImpl(player)

    fun healPlayerPokemon(player: ServerPlayer) = healPlayerPokemonImpl(player)

    fun isPlayerInRankedBattle(player: ServerPlayer): Boolean {
        val battle = Cobblemon.battleRegistry.getBattleByParticipatingPlayer(player) ?: return false
        return battleToIdMap.containsKey(battle)
    }

    private fun restoreAndHealPlayers(vararg players: ServerPlayer) = restoreAndHealPlayersImpl(*players)

    private fun clearBattleState(vararg players: ServerPlayer) = clearBattleStateImpl(*players)

    private fun releaseArenaForPlayers(vararg players: ServerPlayer) = releaseArenaForPlayersImpl(*players)

    fun setPlayerInRankedBattle(playerId: UUID, inBattle: Boolean) = setPlayerInRankedBattleImpl(playerId, inBattle)

    fun isPlayerBlockBreakingRestricted(playerId: UUID): Boolean = isPlayerBlockBreakingRestrictedImpl(playerId)

    fun cleanupStaleRankedBattleMarkers(server: MinecraftServer) = cleanupStaleRankedBattleMarkersImpl(server)

    fun clearAllRankedBattleMarkers() = clearAllRankedBattleMarkersImpl()

    fun tick(server: MinecraftServer) {
        processTurnTimeoutsImpl(server)
        processPendingServerActionsImpl(server)
    }

    fun register() {
        CobblemonEvents.BATTLE_VICTORY.subscribe { event: BattleVictoryEvent ->
            val battle = event.battle
            val battleId = battleToIdMap[battle]
            val format = battleId?.let { rankedBattles[it] }
            if (battleId == null || format == null) return@subscribe

            try {
                if (format == "2v2singles") {
                    DuoBattleManager.updateBattleState(battle)
                    val winners = event.winners.filterIsInstance<PlayerBattleActor>()
                    val losers = event.losers.filterIsInstance<PlayerBattleActor>()
                    if (winners.size == 1 && losers.size == 1) {
                        val winnerId = winners.first().uuid
                        val loserId = losers.first().uuid
                        DuoBattleManager.handleVictory(winnerId, loserId)
                    }
                } else {
                    onBattleVictory(event)
                }
            } finally {
                finalBattleCleanup(battle, battleId)
            }
        }
    }

    fun handleBlockBreak(event: BlockEvent.BreakEvent) {
        val player = event.player
        if (player !is ServerPlayer) return
        if (!config.preventBlockBreaking) return
        if (isPlayerBlockBreakingRestricted(player.uuid)) {
            event.isCanceled = true
        }
    }

    fun handlePlayerDisconnect(player: ServerPlayer, server: MinecraftServer) {
        val battle = Cobblemon.battleRegistry.getBattleByParticipatingPlayer(player)
        server.execute {
            try {
                val battleId = if (battle != null) battleToIdMap[battle] else null

                if (battle != null && battleId != null && rankedBattles.containsKey(battleId)) {
                    handleDisconnectAsFlee(battle, player, server)
                } else {
                    forceCleanupPlayerBattleData(player)
                    DuoBattleManager.handlePlayerQuit(player)

                    val arena = playerToArena.remove(player.uuid)
                    if (arena != null) releaseArena(arena)

                    removePlayerFromWaitingQueue(player.uuid)
                }

                setPlayerInRankedBattle(player.uuid, false)
            } catch (e: Exception) {
                logger.error("Error handling player disconnect", e)
                forceCleanupPlayerBattleData(player)
                setPlayerInRankedBattle(player.uuid, false)
            }
        }
    }

    fun handlePlayerJoin(player: ServerPlayer, server: MinecraftServer) {
        server.execute {
            setPlayerInRankedBattle(player.uuid, false)
        }
    }

    fun handleSelectionPhaseDisconnect(winner: ServerPlayer, loser: ServerPlayer, formatName: String) {
        val seasonId = seasonManager.currentSeasonId
        val winnerData = getOrCreatePlayerData(winner.uuid, seasonId, formatName).apply { playerName = winner.name.string }
        val loserData = getOrCreatePlayerData(loser.uuid, seasonId, formatName).apply { playerName = loser.name.string }

        val oldWinnerElo = winnerData.elo
        val oldLoserElo = loserData.elo

        val (newWinnerElo, newLoserElo) = RankUtils.calculateElo(winnerData.elo, loserData.elo, config.eloKFactor, config.minElo, config.loserProtectionRate)

        val eloDiffWinner = newWinnerElo - oldWinnerElo
        val eloDiffLoser = newLoserElo - oldLoserElo

        winnerData.apply { elo = newWinnerElo; wins++; winStreak++; if (winStreak > bestWinStreak) bestWinStreak = winStreak }
        loserData.apply { elo = newLoserElo; losses++; winStreak = 0; fleeCount++ }

        rankDao.savePlayerData(winnerData)
        rankDao.savePlayerData(loserData)

        releaseArenaForPlayers(winner, loser)
        restoreAndHealPlayers(winner, loser)
        clearBattleState(winner, loser)

        if (!winner.isDisconnected) {
            RankUtils.sendMessage(winner, MessageConfig.get("battle.disconnect.winner", config.defaultLang, "elo" to winnerData.elo.toString()))
            sendBattleResultMessage(winner, winnerData, eloDiffWinner)
            grantVictoryRewards(winner, winner.server)
            teleportBackIfPossible(winner)
            rewardManager.grantRankRewardIfEligible(winner, winnerData.getRankTitle(), formatName, winner.server)
        }
    }

    private fun handleDisconnectAsFlee(battle: PokemonBattle, disconnected: ServerPlayer, server: MinecraftServer) {
        val battleId = battleToIdMap[battle]
        val formatName = battleId?.let { rankedBattles[it] } ?: return

        try {
            if (formatName == "2v2singles") {
                val arenaToRelease = battleId?.let { battleIdToArena[it] } ?: playerToArena[disconnected.uuid]
                DuoBattleManager.handlePlayerQuit(disconnected)
                cleanupBattleData(battle)
                clearBattleState(disconnected)
                releaseArenaForPlayers(disconnected)
                battleId?.let { battleIdToArena.remove(it) }
                arenaToRelease?.let(::releaseArena)
                return
            }

            val seasonId = seasonManager.currentSeasonId
            val actors = battle.actors.filterIsInstance<PlayerBattleActor>()
            val winnerId = actors.firstOrNull { it.uuid != disconnected.uuid }?.uuid
            val arenaToRelease = battleId?.let { battleIdToArena[it] }
                ?: playerToArena[disconnected.uuid]
                ?: winnerId?.let { playerToArena[it] }
            val winner = winnerId?.let { winnerUuid ->
                server.playerManager.getPlayer(winnerUuid)
                    ?: actors.firstOrNull { it.uuid == winnerUuid }?.entity
            }

            if (winner == null) {
                cleanupBattleData(battle)
                restorePlayerPokemonLevels(disconnected)
                clearBattleState(disconnected)
                releaseArenaForPlayers(disconnected)
                battleId?.let { battleIdToArena.remove(it) }
                arenaToRelease?.let(::releaseArena)
                return
            }

            markPokemonAsUsed(disconnected.uuid, UUID.randomUUID())

            val winnerData = getOrCreatePlayerData(winner.uuid, seasonId, formatName).apply { playerName = winner.name.string }
            val loserData = getOrCreatePlayerData(disconnected.uuid, seasonId, formatName).apply { playerName = disconnected.name.string }

            val oldWinnerElo = winnerData.elo
            val oldLoserElo = loserData.elo

            val (newWinnerElo, newLoserElo) = RankUtils.calculateElo(winnerData.elo, loserData.elo, config.eloKFactor, config.minElo, config.loserProtectionRate)

            val eloDiffWinner = newWinnerElo - oldWinnerElo
            val eloDiffLoser = newLoserElo - oldLoserElo

            winnerData.apply { elo = newWinnerElo; wins++; winStreak++; if (winStreak > bestWinStreak) bestWinStreak = winStreak }
            loserData.apply { elo = newLoserElo; losses++; winStreak = 0; fleeCount++ }

            rankDao.savePlayerData(winnerData)
            rankDao.savePlayerData(loserData)

            restorePlayerPokemonLevels(disconnected)
            healPlayerPokemon(disconnected)
            clearBattleState(disconnected)

            fun finalizeDisconnectSettlement(attempt: Int) {
                scheduleServerAction(
                    if (attempt == 0) DISCONNECT_SETTLEMENT_DELAY_MILLIS else DISCONNECT_SETTLEMENT_RETRY_DELAY_MILLIS
                ) { delayedServer ->
                    val onlineWinner = winnerId?.let(delayedServer.playerManager::getPlayer)
                    val winnerStillInBattle = onlineWinner != null &&
                        Cobblemon.battleRegistry.getBattleByParticipatingPlayer(onlineWinner) != null

                    if (winnerStillInBattle && attempt < DISCONNECT_SETTLEMENT_MAX_RETRIES) {
                        finalizeDisconnectSettlement(attempt + 1)
                        return@scheduleServerAction
                    }

                    if (onlineWinner != null) {
                        restoreAndHealPlayers(onlineWinner)
                        clearBattleState(onlineWinner)

                        if (!onlineWinner.isDisconnected) {
                            RankUtils.sendMessage(
                                onlineWinner,
                                MessageConfig.get("battle.disconnect.winner", config.defaultLang, "elo" to winnerData.elo.toString())
                            )
                            sendBattleResultMessage(onlineWinner, winnerData, eloDiffWinner)
                            grantVictoryRewards(onlineWinner, onlineWinner.server)
                            teleportBackIfPossible(onlineWinner)
                            rewardManager.grantRankRewardIfEligible(
                                onlineWinner,
                                winnerData.getRankTitle(),
                                formatName,
                                onlineWinner.server
                            )
                        }
                    }

                    playerToArena.remove(disconnected.uuid)
                    winnerId?.let(playerToArena::remove)
                    battleId?.let { battleIdToArena.remove(it) }
                    arenaToRelease?.let(::releaseArena)
                }
            }

            finalizeDisconnectSettlement(0)

        } catch (e: Exception) {
            logger.error("Error handling disconnect as flee", e)
            val arenaToRelease = battleId?.let { battleIdToArena[it] } ?: playerToArena[disconnected.uuid]
            restorePlayerPokemonLevels(disconnected)
            clearBattleState(disconnected)
            releaseArenaForPlayers(disconnected)
            battleId?.let { battleIdToArena.remove(it) }
            arenaToRelease?.let(::releaseArena)
        } finally {
            battleToIdMap.remove(battle)
        }
    }

    fun markAsRanked(battleId: UUID, formatName: String) {
        rankedBattles[battleId] = formatName
    }

    fun registerBattle(battle: PokemonBattle, battleId: UUID) {
        battleToIdMap[battle] = battleId
        val arena = battle.actors
            .filterIsInstance<PlayerBattleActor>()
            .asSequence()
            .mapNotNull { actor -> playerToArena[actor.uuid] ?: actor.entity?.let { playerToArena[it.uuid] } }
            .firstOrNull()
        if (arena != null) {
            battleIdToArena[battleId] = arena
        }

        battle.actors.filterIsInstance<PlayerBattleActor>().forEach { actor ->
            actor.entity?.let { entity ->
                setPlayerInRankedBattle(entity.uuid, true)
            }
        }
    }

    fun onBattleVictory(event: BattleVictoryEvent) {
        val battle = event.battle
        val battleId = battleToIdMap[battle]
        val formatName = battleId?.let { rankedBattles[it] }
        if (battleId == null || formatName == null) return

        val winners = extractPlayerActors(event.winners).mapNotNull { it.entity }
        val losers = extractPlayerActors(event.losers).mapNotNull { it.entity }

        val winner = winners.firstOrNull()
        val loser = losers.firstOrNull()

        if (winner != null && loser != null) {
            val seasonId = seasonManager.currentSeasonId
            val winnerData = getOrCreatePlayerData(winner.uuid, seasonId, formatName)
            val loserData = getOrCreatePlayerData(loser.uuid, seasonId, formatName)
            winnerData.playerName = winner.name.string
            loserData.playerName = loser.name.string

            val (newWinnerElo, newLoserElo) = RankUtils.calculateElo(winnerData.elo, loserData.elo, config.eloKFactor, config.minElo, config.loserProtectionRate)
            val eloDiffWinner = newWinnerElo - winnerData.elo
            val eloDiffLoser = newLoserElo - loserData.elo

            winnerData.apply { elo = newWinnerElo; wins++; winStreak++; if (winStreak > bestWinStreak) bestWinStreak = winStreak }
            loserData.apply { elo = newLoserElo; losses++; winStreak = 0 }

            rankDao.savePlayerData(winnerData)
            rankDao.savePlayerData(loserData)

            grantVictoryRewards(winner, winner.server)
            recordBattlePokemonUsage(battle, seasonId)
            sendBattleResultMessage(winner, winnerData, eloDiffWinner)
            sendBattleResultMessage(loser, loserData, eloDiffLoser)
            rewardManager.grantRankRewardIfEligible(winner, winnerData.getRankTitle(), formatName, winner.server)
            rewardManager.grantRankRewardIfEligible(loser, loserData.getRankTitle(), formatName, loser.server)

            restoreAndHealPlayers(winner, loser)

            teleportBackIfPossible(winner)
            teleportBackIfPossible(loser)

            releaseArenaForPlayers(winner, loser)
            clearBattleState(winner, loser)
        }
    }

    fun markPokemonAsUsed(playerUuid: UUID, pokemonUuid: UUID) {
        usedPokemonUuids.computeIfAbsent(playerUuid) { mutableSetOf() }.add(pokemonUuid)
        logger.debug("Marked Pokemon $pokemonUuid as used for player $playerUuid")
    }

    fun isPokemonUsed(playerUuid: UUID, pokemonUuid: UUID): Boolean {
        return usedPokemonUuids[playerUuid]?.contains(pokemonUuid) == true
    }

    fun getUsedPokemonCount(playerUuid: UUID): Int {
        return usedPokemonUuids[playerUuid]?.size ?: 0
    }

    fun getUsedPokemonSet(playerUuid: UUID): Set<UUID> {
        return usedPokemonUuids[playerUuid]?.toSet() ?: emptySet()
    }

    fun clearPlayerUsedPokemon(playerUuid: UUID) = clearPlayerUsedPokemonImpl(playerUuid)

    fun clearAllUsedPokemon() = clearAllUsedPokemonImpl()

    fun cleanupStaleUsedPokemonMarkers(server: MinecraftServer) = cleanupStaleUsedPokemonMarkersImpl(server)

    fun grantVictoryRewards(winner: ServerPlayer, server: MinecraftServer) = grantVictoryRewardsImpl(winner, server)

    fun teleportBackIfPossible(player: Player) = teleportBackIfPossibleImpl(player)

    private fun extractPlayerActors(actors: List<BattleActor>): List<PlayerBattleActor> = actors.filterIsInstance<PlayerBattleActor>()

    private fun getOrCreatePlayerData(playerId: UUID, seasonId: Int, format: String): PlayerRankData {
        return rankDao.getPlayerData(playerId, seasonId, format) ?: PlayerRankData(playerId = playerId, seasonId = seasonId, format = format).apply { elo = config.initialElo }
    }

    fun sendBattleResultMessage(player: Player, data: PlayerRankData, eloChange: Int) =
        sendBattleResultMessageImpl(player, data, eloChange)

    fun grantRankReward(player: Player, rank: String, format: String, server: MinecraftServer): Boolean =
        grantRankRewardImpl(player, rank, format, server)

    internal fun executeRewardCommand(command: String, player: Player, server: MinecraftServer) =
        executeRewardCommandImpl(command, player, server)

    private fun recordBattlePokemonUsage(battle: PokemonBattle, seasonId: Int) = recordBattlePokemonUsageImpl(battle, seasonId)

    fun recordSelectedPokemonUsage(player: ServerPlayer, selectedPokemon: List<UUID>, seasonId: Int) =
        recordSelectedPokemonUsageImpl(player, selectedPokemon, seasonId)

    fun forceCleanupPlayerBattleData(player: ServerPlayer) = forceCleanupPlayerBattleDataImpl(player)
}

