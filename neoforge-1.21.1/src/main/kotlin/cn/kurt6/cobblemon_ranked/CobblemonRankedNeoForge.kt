package cn.kurt6.cobblemon_ranked

import cn.kurt6.cobblemon_ranked.battle.BattleHandler
import cn.kurt6.cobblemon_ranked.matchmaking.MatchmakingQueue
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.fml.common.Mod

@Mod(CobblemonRanked.MOD_ID)
class CobblemonRankedNeoForge(modEventBus: IEventBus) {
    private val mod = CobblemonRanked()

    init {
        mod.initialize()

        modEventBus.addListener(this::onRegisterPayloadHandlers)

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(this::onServerStarted)
        NeoForge.EVENT_BUS.addListener(this::onServerStopping)
        NeoForge.EVENT_BUS.addListener(this::onServerTick)
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn)
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut)
        NeoForge.EVENT_BUS.addListener(this::onPlayerRespawn)
        NeoForge.EVENT_BUS.addListener(this::onBlockBreak)
    }

    private fun onRegisterPayloadHandlers(event: RegisterPayloadHandlersEvent) {
        cn.kurt6.cobblemon_ranked.network.NeoForgeNetworking.register(event)
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        mod.registerCommands(event.dispatcher)
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        mod.handleServerStarted(event.server)
    }

    private fun onServerStopping(event: ServerStoppingEvent) {
        mod.handleServerStopping()
    }

    private fun onServerTick(event: ServerTickEvent.Pre) {
        mod.handleServerTick(event.server)
    }

    private fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        NeoForgeDisconnectBridge.clearDisconnectState(player)
        BattleHandler.handlePlayerJoin(player, player.server)
        mod.handlePlayerJoin(player)
    }

    private fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        NeoForgeDisconnectBridge.handleDisconnect(player)
    }

    private fun onPlayerRespawn(event: PlayerEvent.PlayerRespawnEvent) {
        val player = event.entity as? ServerPlayer ?: return
        MatchmakingQueue.handlePlayerRespawn(player)
    }

    private fun onBlockBreak(event: BlockEvent.BreakEvent) {
        BattleHandler.handleBlockBreak(event)
    }
}
