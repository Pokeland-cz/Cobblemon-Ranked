// CrossCommand.kt
package cn.kurt6.cobblemon_ranked.crossserver

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.battle.BattleHandler
import cn.kurt6.cobblemon_ranked.battle.TeamSelectionManager
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.crossserver.CrossServerSocket.webSocket
import cn.kurt6.cobblemon_ranked.matchmaking.DuoMatchmakingQueue
import cn.kurt6.cobblemon_ranked.util.*
import cn.kurt6.cobblemon_ranked.util.RankUtils
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.battles.BattleFormat
import com.google.gson.JsonObject
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.arguments.IntegerArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

object CrossCommand {
    fun register(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("rank").then(
            Commands.literal("cross").apply {
                then(
                    Commands.literal("join")
                        .then(Commands.argument("mode", StringArgumentType.word())
                            .suggests { _, builder ->
                                builder.suggest("singles")
//                                builder.suggest("doubles")
                                builder.buildFuture()
                            }
                            .executes { context ->
                                val source = context.source
                                val player = source.player ?: run {
                                    source.sendError(
                                        MessageConfig.get("command.only_player",
                                        CobblemonRanked.config.defaultLang))
                                    return@executes Command.SINGLE_SUCCESS
                                }

                                if (webSocket == null) {
                                    RankUtils.sendMessage(player, MessageConfig.get("cross.queue.not_connected", CobblemonRanked.config.defaultLang))
                                    return@executes Command.SINGLE_SUCCESS
                                }

                                // +++ 使用命令标签检查状态 +++
                                if (player.commandTags.contains("ranked_cross_in_queue") ||
                                    player.commandTags.contains("ranked_cross_in_battle1")) {
                                    source.sendError(MessageConfig.get("command.battle.in_queue_or_battle",
                                        CobblemonRanked.config.defaultLang))
                                    return@executes Command.SINGLE_SUCCESS
                                }

                                // +++ 验证是否为正版玩家 +++
//                                if (!player.gameProfile.properties.containsKey("textures")) {
//                                    source.sendError(MessageConfig.get("command.join.authenticated_only", CobblemonRanked.config.defaultLang))
//                                    return@executes Command.SINGLE_SUCCESS
//                                }

                                val mode = StringArgumentType.getString(context, "mode")
                                if (!canJoinCrossQueue(player)) {
                                    RankUtils.sendMessage(player, MessageConfig.get("queue.cannot_join", CobblemonRanked.config.defaultLang))
                                    return@executes Command.SINGLE_SUCCESS
                                }

                                val teamUuids = Cobblemon.storage.getParty(player)
                                    .filterNotNull()
                                    .map { it.uuid }
                                if (!BattleHandler.validateTeam(player, teamUuids, BattleFormat.GEN_9_SINGLES)) {
                                    return@executes Command.SINGLE_SUCCESS
                                }

                                val team = Utils.getPokemonTeam(player)

                                if (team.isEmpty()) {
                                    source.sendError(MessageConfig.get("command.join.empty_team",
                                        CobblemonRanked.config.defaultLang))
                                    return@executes Command.SINGLE_SUCCESS
                                }

                                CrossServerSocket.joinMatchmakingQueue(player, team, mode)
                                Command.SINGLE_SUCCESS
                            }
                        )
                )
                .then(
                    Commands.literal("leave")
                        .executes { context ->
                            val source = context.source
                            val player = source.player ?: run {
                                source.sendError(MessageConfig.get("command.only_player",
                                    CobblemonRanked.config.defaultLang))
                                return@executes Command.SINGLE_SUCCESS
                            }

                            if (webSocket == null) {
                                RankUtils.sendMessage(player, MessageConfig.get("cross.queue.not_connected", CobblemonRanked.config.defaultLang))
                                return@executes Command.SINGLE_SUCCESS
                            }

                            // +++ 使用命令标签检查状态 +++
                            if (!player.commandTags.contains("ranked_cross_in_queue")) {
                                source.sendError(MessageConfig.get("command.not_in_queue",
                                    CobblemonRanked.config.defaultLang))
                                return@executes Command.SINGLE_SUCCESS
                            }

                            CrossServerSocket.leaveMatchmakingQueue(player)
                            Command.SINGLE_SUCCESS
                        }
                )
                .then(
                    Commands.literal("start")
                        .requires { it.hasPermissionLevel(2) }
                        .executes {context ->
                            CrossServerSocket.connect(context.source)
                            context.source.sendSuccess(MessageConfig.get("command.connect.start",
                                CobblemonRanked.config.defaultLang))
                            Command.SINGLE_SUCCESS
                        }
                    )
                .then(
                    Commands.literal("stop")
                        .requires { it.hasPermissionLevel(2) }
                        .executes {
                            CrossServerSocket.disconnect()
                            it.source.sendSuccess(MessageConfig.get("command.connect.stop",
                                CobblemonRanked.config.defaultLang))
                            Command.SINGLE_SUCCESS
                        }
                )
                // 战斗相关指令 - 只在战斗状态下可用
                .then(
                    Commands.literal("battle").apply {
                        requires { source ->
                        val player = source.player
                        player != null && getBattleSession(player) != null
                        }
                        .apply {
                            then(Commands.literal("move")
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, 4))
                                    .suggests { context, builder ->
                                        val player = context.source.player ?: return@suggests builder.buildFuture()
                                        getAvailableMoveOptions(player).forEach { (slot, moveName) ->
                                            builder.suggest(slot.toString(), Component.literal(moveName))
                                        }
                                        builder.buildFuture()
                                    }
                                    .executes { context ->
                                        val source = context.source
                                        val player = source.player ?: run {
                                            source.sendError(MessageConfig.get("command.only_player",
                                                CobblemonRanked.config.defaultLang))
                                            return@executes Command.SINGLE_SUCCESS
                                        }

                                        // 获取 slot 参数，确保其在有效范围内
                                        val slot = IntegerArgumentType.getInteger(context, "slot")
                                        if (slot < 1 || slot > 4) {
                                            source.sendError(MessageConfig.get("command.battle.invalid_move_slot",
                                                CobblemonRanked.config.defaultLang))
                                            return@executes Command.SINGLE_SUCCESS
                                        }

                                        // 发送战斗指令，确保 slot 转换为字符串传递
                                        CrossServerSocket.sendBattleCommand(player, "move", slot.toString())
                                        Command.SINGLE_SUCCESS
                                    }
                                )
                            )
                            .then(
                                Commands.literal("switch")
                                    .then(Commands.argument("slot", IntegerArgumentType.integer(1, 6))
                                        .suggests { context, builder ->
                                            val player = context.source.player ?: return@suggests builder.buildFuture()
                                            getAvailableSwitchOptions(player).forEach { (slot, name) ->
                                                builder.suggest(slot.toString(), Component.literal(name))
                                            }
                                            builder.buildFuture()
                                        }
                                        .executes { context ->
                                            val source = context.source
                                            val player = source.player ?: run {
                                                source.sendError(MessageConfig.get("command.only_player",
                                                    CobblemonRanked.config.defaultLang))
                                                return@executes Command.SINGLE_SUCCESS
                                            }

                                            val slot = IntegerArgumentType.getInteger(context, "slot")
                                            // 检查槽位是否有效
                                            if (slot < 1 || slot > 6) {
                                                source.sendError(MessageConfig.get("command.battle.invalid_switch_slot",
                                                    CobblemonRanked.config.defaultLang))
                                                return@executes Command.SINGLE_SUCCESS
                                            }

                                            // 发送战斗指令，确保 slot 转换为字符串传递
                                            CrossServerSocket.sendBattleCommand(player, "switch", slot.toString())
                                            Command.SINGLE_SUCCESS
                                        }
                                    )
                            )
                            .then(
                                Commands.literal("forfeit")
                                    .executes { context ->
                                        val source = context.source
                                        val player = source.player ?: run {
                                            source.sendError(MessageConfig.get("command.only_player",
                                                CobblemonRanked.config.defaultLang))
                                            return@executes Command.SINGLE_SUCCESS
                                        }

                                        CrossServerSocket.sendBattleCommand(player, "forfeit", "")
                                        Command.SINGLE_SUCCESS
                                    }
                            )
                        }
                    }
                )

                // 聊天指令 - 只在战斗状态下可用
                .then(
                    Commands.literal("chat")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes { context ->
                                val source = context.source
                                val player = source.player ?: run {
                                    source.sendError(MessageConfig.get("command.only_player",
                                        CobblemonRanked.config.defaultLang))
                                    return@executes Command.SINGLE_SUCCESS
                                }

                                val message = StringArgumentType.getString(context, "message")
                                CrossServerSocket.sendChatMessage(player, message)
                                Command.SINGLE_SUCCESS
                            }
                        )
                )
            }
        )
    }

    // 简化消息发送
    private fun CommandSourceStack.sendSuccess(key: String, vararg params: Pair<String, Any>) {
        val lang = CobblemonRanked.config.defaultLang
        sendMessage(Component.literal(MessageConfig.get(key, lang, *params)))
    }

    private fun CommandSourceStack.sendError(key: String, vararg params: Pair<String, Any>) {
        val lang = CobblemonRanked.config.defaultLang
        sendMessage(Component.literal(MessageConfig.get(key, lang, *params))
            .styled { style -> style.withColor(0xFF5555) })
    }

    private fun canJoinCrossQueue(player: ServerPlayer): Boolean {
        if (Cobblemon.battleRegistry.getBattleByParticipatingPlayer(player) != null) return false
        if (TeamSelectionManager.isPlayerInSelection(player.uuid)) return false
        if (BattleHandler.isPlayerWaitingForArena(player.uuid)) return false
        if (CobblemonRanked.matchmakingQueue.getPlayer(player.uuid) != null) return false
        if (DuoMatchmakingQueue.isInQueue(player.uuid)) return false
        return !player.isDisconnected
    }

    private fun getBattleSession(player: ServerPlayer): CrossServerSocket.BattleSession? {
        return CrossServerSocket.battleSessions[player.uuidAsString]
    }

    private fun getAvailableMoveOptions(player: ServerPlayer): List<Pair<Int, String>> {
        val session = getBattleSession(player) ?: return emptyList()
        val activeIndex = session.selfActiveIndex
        val initialPokemon = session.selfTeam.getOrNull(activeIndex) ?: return emptyList()
        val currentPokemon = session.currentSelfTeam.getOrNull(activeIndex) ?: return emptyList()
        val initialMoves = initialPokemon.getAsJsonArray("moves") ?: return emptyList()
        val currentMoves = currentPokemon.getAsJsonArray("moves") ?: return emptyList()

        return buildList {
            for (i in 0 until minOf(4, initialMoves.size(), currentMoves.size())) {
                val currentMove = currentMoves.get(i).asJsonObject
                val currentPp = currentMove["current_pp"]?.asInt ?: 0
                if (currentPp <= 0) continue

                val initialMove = initialMoves.get(i).asJsonObject
                add((i + 1) to (initialMove["name"]?.asString ?: "???"))
            }
        }
    }

    private fun getAvailableSwitchOptions(player: ServerPlayer): List<Pair<Int, String>> {
        val session = getBattleSession(player) ?: return emptyList()

        return buildList {
            session.currentSelfTeam.forEachIndexed { index, pokemon ->
                if (index == session.selfActiveIndex) return@forEachIndexed

                val hp = pokemon["hp"]?.asInt ?: 0
                if (hp <= 0) return@forEachIndexed

                add((index + 1) to getPokemonName(session, index, pokemon))
            }
        }
    }

    private fun getPokemonName(session: CrossServerSocket.BattleSession, index: Int, fallbackPokemon: JsonObject): String {
        return session.selfTeam.getOrNull(index)?.get("name")?.asString
            ?: fallbackPokemon.get("name")?.asString
            ?: "???"
    }
}

