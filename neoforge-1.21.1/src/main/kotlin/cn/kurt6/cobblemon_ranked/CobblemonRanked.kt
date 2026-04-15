package cn.kurt6.cobblemon_ranked

import cn.kurt6.cobblemon_ranked.battle.BattleHandler
import cn.kurt6.cobblemon_ranked.battle.DuoBattleManager
import cn.kurt6.cobblemon_ranked.commands.RankCommands
import cn.kurt6.cobblemon_ranked.config.ConfigManager
import cn.kurt6.cobblemon_ranked.config.DatabaseConfig
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.config.RankConfig
import cn.kurt6.cobblemon_ranked.crossserver.CrossCommand
import cn.kurt6.cobblemon_ranked.crossserver.CrossServerSocket
import cn.kurt6.cobblemon_ranked.data.RankDao
import cn.kurt6.cobblemon_ranked.data.RewardManager
import cn.kurt6.cobblemon_ranked.data.SeasonManager
import cn.kurt6.cobblemon_ranked.matchmaking.DuoMatchmakingQueue
import cn.kurt6.cobblemon_ranked.matchmaking.MatchmakingQueue
import cn.kurt6.cobblemon_ranked.util.RankPlaceholders
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.server.MinecraftServer
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

class CobblemonRanked {
    private var tickCounter = 0
    private var cleanupCounter = 0

    fun initialize() {
        INSTANCE = this
        logger.info("Initializing Cobblemon Ranked Mod")

        dataPath = Path.of("config").resolve(MOD_ID).apply { toFile().mkdirs() }
        config = ConfigManager.load()

        databaseConfig = ConfigManager.loadDatabaseConfig()

        MessageConfig.get("msg_example")

        rankDao = RankDao(databaseConfig, dataPath.toFile())
        rewardManager = RewardManager(rankDao)
        seasonManager = SeasonManager(rankDao)

        matchmakingQueue = MatchmakingQueue()

        BattleHandler.register()

        RankPlaceholders.register()

        logger.info("Cobblemon Ranked Mod initialized with database: ${databaseConfig.databaseType}")
    }

    fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        RankCommands.register(dispatcher)
        dispatcher.register(CrossCommand.register())
    }

    fun handleServerStopping() {
        try {
            BattleHandler.clearAllRankedBattleMarkers()
            BattleHandler.clearAllUsedPokemon()

            matchmakingQueue.shutdown()
            DuoMatchmakingQueue.shutdown()
            DuoBattleManager.shutdown()
            matchmakingQueue.clear()
            CrossServerSocket.disconnect()
            cn.kurt6.cobblemon_ranked.battle.TeamSelectionManager.shutdown()
            DuoBattleManager.clearAll()

            rankDao.close()
        } catch (e: Exception) {
            logger.warn("Error during server stopping", e)
        }
    }

    fun handleServerStarted(server: MinecraftServer) {
        tickCounter = 0
        cleanupCounter = 0
        BattleHandler.clearAllRankedBattleMarkers()
        BattleHandler.clearAllUsedPokemon()
        logger.info("Cleared all ranked battle markers and used Pokemon on server start")
    }

    fun handleServerTick(server: MinecraftServer) {
        BattleHandler.tick(server)

        if (++tickCounter >= SEASON_CHECK_INTERVAL) {
            tickCounter = 0
            seasonManager.checkSeasonEnd(server)
        }

        if (++cleanupCounter >= CLEANUP_INTERVAL) {
            cleanupCounter = 0
            CompletableFuture.runAsync {
                try {
                    rankDao.cleanupOldReturnLocations()
                    logger.info("Performed daily cleanup of old return locations")
                } catch (e: Exception) {
                    logger.warn("Failed to cleanup old return locations", e)
                }
            }
            server.execute {
                BattleHandler.cleanupStaleRankedBattleMarkers(server)
                BattleHandler.cleanupStaleUsedPokemonMarkers(server)
            }
        }
    }

    fun handlePlayerJoin(player: ServerPlayer) {
        val server = player.server
        server.execute {
            BattleHandler.teleportBackIfPossible(player)
            BattleHandler.restorePlayerPokemonLevels(player)
        }
        CrossServerSocket.handlePlayerJoin(player)
    }

    fun handlePlayerDisconnect(player: ServerPlayer) {
        cn.kurt6.cobblemon_ranked.battle.TeamSelectionManager.handleDisconnect(player)
        cn.kurt6.cobblemon_ranked.util.ClientVersionTracker.removePlayer(player.uuid)
        CrossServerSocket.handlePlayerDisconnect(player)
    }

    companion object {
        private const val SEASON_CHECK_INTERVAL = 20 * 60 * 10
        private const val CLEANUP_INTERVAL = 20 * 60 * 60 * 24

        const val MOD_ID = "cobblemon_ranked"
        val logger = LoggerFactory.getLogger(MOD_ID)

        lateinit var INSTANCE: CobblemonRanked
        lateinit var config: RankConfig
        lateinit var databaseConfig: DatabaseConfig
        lateinit var dataPath: Path
        lateinit var rankDao: RankDao
        lateinit var matchmakingQueue: MatchmakingQueue
        lateinit var seasonManager: SeasonManager
        lateinit var rewardManager: RewardManager
    }
}
