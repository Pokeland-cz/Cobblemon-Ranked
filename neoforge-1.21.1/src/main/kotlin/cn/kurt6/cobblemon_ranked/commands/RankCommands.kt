package cn.kurt6.cobblemon_ranked.commands

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.config.ConfigManager
import cn.kurt6.cobblemon_ranked.config.MessageConfig
import cn.kurt6.cobblemon_ranked.matchmaking.DuoMatchmakingQueue
import cn.kurt6.cobblemon_ranked.util.*
import cn.kurt6.cobblemon_ranked.util.RankUtils
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

object RankCommands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("rank")
                .then(Commands.literal("pokemon_usage")
                    .executes { showPokemonUsage(it, CobblemonRanked.seasonManager.currentSeasonId, 1) }
                        .then(Commands.argument("season", IntegerArgumentType.integer(1))
                            .executes { ctx ->
                                val season = IntegerArgumentType.getInteger(ctx, "season")
                                showPokemonUsage(ctx, season, 1)
                            }
                            .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes { ctx ->
                                    val season = IntegerArgumentType.getInteger(ctx, "season")
                                    val page = IntegerArgumentType.getInteger(ctx, "page")
                                    showPokemonUsage(ctx, season, page)
                                }
                            )
                        )
                )
                .then(Commands.literal("gui")
                    .executes { ctx ->
                        val player = ctx.source.player ?: return@executes 0
                        showRankGui(player)
                        1
                    }
                )
                .then(Commands.literal("gui_top")
                    .executes { ctx ->
                        val player = ctx.source.player ?: return@executes 0
                        showTopMenu(player)
                        1
                    }
                )
                .then(Commands.literal("gui_info")
                    .executes { ctx ->
                        val player = ctx.source.player ?: return@executes 0
                        showInfoMenu(player)
                        1
                    }
                )
                .then(Commands.literal("gui_info_players")
                    .executes { ctx ->
                        val player = ctx.source.player ?: return@executes 0
                        showInfoPlayerMenu(player, 1)
                        1
                    }
                )
                .then(Commands.literal("gui_queue")
                    .executes { ctx ->
                        val player = ctx.source.player ?: return@executes 0
                        showQueueMenu(player)
                        1
                    }
                )
                .then(Commands.literal("gui_reward")
                    .executes { ctx ->
                        val player = ctx.source.player ?: return@executes 0
                        showRewardFormatMenu(player)
                        1
                    }
                )
                .then(Commands.literal("gui_info_format")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("format", StringArgumentType.word())
                            .executes { ctx ->
                                val source = ctx.source
                                val lang = CobblemonRanked.config.defaultLang
                                val playerName = StringArgumentType.getString(ctx, "player")
                                val format = StringArgumentType.getString(ctx, "format")
                                val season = CobblemonRanked.seasonManager.currentSeasonId
                                val target = source.server.playerManager.getPlayer(playerName)
                                if (target != null) {
                                    showInfoMenuForPlayer(source.player, target, format, season)
                                } else {
                                    source.sendMessage(Component.literal(MessageConfig.get("player.not_found", lang, "player" to playerName)))
                                }
                                1
                            }
                        )
                    )
                )
                .then(Commands.literal("gui_myinfo")
                    .executes { ctx ->
                        val season = CobblemonRanked.seasonManager.currentSeasonId
                        val player = ctx.source.player ?: return@executes 0
                        showMyInfoMenu(player, season)
                        1
                    }
                )
                .then(Commands.literal("gui_reset")
                    .executes { ctx ->
                        val player = ctx.source.player ?: return@executes 0
                        showResetPlayerList(player, 1)
                        1
                    }
                )
                .then(Commands.literal("reset")
                    .requires { it.hasPermissionLevel(4) }
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("format", StringArgumentType.word())
                            .executes { resetPlayerRank(it) }
                        )
                    )
                )
                .then(Commands.literal("reload")
                    .requires { source -> source.hasPermissionLevel(4) }
                    .executes {
                        val newConfig = ConfigManager.reload()
                        val lang = newConfig.defaultLang

                        it.source.sendMessage(Component.literal(MessageConfig.get("config.reloaded", lang)))
                        1
                    }
                )
                .then(Commands.literal("queue")
                    .then(Commands.literal("join")
                        .executes { joinQueue(it, null) }
                        .then(Commands.argument("format", StringArgumentType.word())
                            .executes { ctx ->
                                val format = StringArgumentType.getString(ctx, "format")
                                if (!validateFormatOrFail(format, ctx.source)) return@executes 0
                                joinQueue(ctx, format)
                            }
                        )
                    )
                    .then(Commands.literal("leave")
                        .executes {
                            val player = it.source.player ?: return@executes 0
                            CobblemonRanked.matchmakingQueue.removePlayer(player.uuid)
                            DuoMatchmakingQueue.removePlayer(player)
                            val lang = CobblemonRanked.config.defaultLang
                            player.sendMessage(Component.literal(MessageConfig.get("queue.leave", lang)))
                            1
                        }
                    )
                )
                .then(Commands.literal("info")
                    .then(Commands.argument("format", StringArgumentType.word())
                        .then(Commands.argument("season", IntegerArgumentType.integer(1))
                            .executes { ctx ->
                                val player = ctx.source.player ?: return@executes 0
                                val format = StringArgumentType.getString(ctx, "format")
                                if (!validateFormatOrFail(format, ctx.source)) return@executes 0
                                val season = IntegerArgumentType.getInteger(ctx, "season")
                                showPlayerRank(ctx, player, format, season)
                            }
                        )
                    )
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("format", StringArgumentType.word())
                            .executes { ctx ->
                                val player = EntityArgument.getPlayer(ctx, "player")
                                val format = StringArgumentType.getString(ctx, "format")
                                if (!validateFormatOrFail(format, ctx.source)) return@executes 0
                                val season = CobblemonRanked.seasonManager.currentSeasonId
                                showPlayerRank(ctx, player, format, season)
                            }
                            .then(Commands.argument("season", IntegerArgumentType.integer(1))
                                .executes { ctx ->
                                    val player = EntityArgument.getPlayer(ctx, "player")
                                    val format = StringArgumentType.getString(ctx, "format")
                                    if (!validateFormatOrFail(format, ctx.source)) return@executes 0
                                    val season = IntegerArgumentType.getInteger(ctx, "season")
                                    showPlayerRank(ctx, player, format, season)
                                }
                            )
                        )
                    )
                )
                .then(Commands.literal("top")
                    .executes { ctx ->
                        val format = CobblemonRanked.config.defaultFormat
                        val season = CobblemonRanked.seasonManager.currentSeasonId
                        showLeaderboard(ctx, format, season, 1, 10)
                    }
                )
                .then(Commands.literal("top")
                    .then(Commands.argument("format", StringArgumentType.word())
                        .executes { ctx ->
                            val format = StringArgumentType.getString(ctx, "format")
                            if (!validateFormatOrFail(format, ctx.source)) return@executes 0
                            val season = CobblemonRanked.seasonManager.currentSeasonId
                            showLeaderboard(ctx, format, season, 1, 10)
                        }
                        .then(Commands.argument("season", IntegerArgumentType.integer(1))
                            .executes { ctx ->
                                val format = StringArgumentType.getString(ctx, "format")
                                if (!validateFormatOrFail(format, ctx.source)) return@executes 0
                                val season = IntegerArgumentType.getInteger(ctx, "season")
                                showLeaderboard(ctx, format, season, 1, 10)
                            }
                            .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes { ctx ->
                                    val format = StringArgumentType.getString(ctx, "format")
                                    if (!validateFormatOrFail(format, ctx.source)) return@executes 0
                                    val season = IntegerArgumentType.getInteger(ctx, "season")
                                    val page = IntegerArgumentType.getInteger(ctx, "page")
                                    showLeaderboard(ctx, format, season, page, 10)
                                }
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                    .executes { ctx ->
                                        val format = StringArgumentType.getString(ctx, "format")
                                        if (!validateFormatOrFail(format, ctx.source)) return@executes 0
                                        val season = IntegerArgumentType.getInteger(ctx, "season")
                                        val page = IntegerArgumentType.getInteger(ctx, "page")
                                        val count = IntegerArgumentType.getInteger(ctx, "count")
                                        showLeaderboard(ctx, format, season, page, count)
                                    }
                                )
                            )
                        )
                    )
                )
                .then(Commands.literal("season")
                    .executes { showSeasonInfo(it) }
                    .then(Commands.literal("end")
                        .requires { source -> source.hasPermissionLevel(4) }
                        .executes { endSeason(it) }
                    )
                )
                .then(Commands.literal("reward")
                    .requires { source -> source.hasPermissionLevel(4) }
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("format", StringArgumentType.word())
                            .then(Commands.argument("rank", StringArgumentType.greedyString())
                                .suggests { context, builder ->
                                    CobblemonRanked.config.rankTitles.values.forEach {
                                        builder.suggest(it)
                                    }
                                    builder.buildFuture()
                                }
                                .executes { grantRankReward(it) }
                            )
                        )
                    )
                )
                .then(Commands.literal("status")
                    .executes { ctx ->
                        val player = ctx.source.player ?: return@executes 0
                        val lang = CobblemonRanked.config.defaultLang
                        val in1v1Queue = CobblemonRanked.matchmakingQueue.getPlayer(player.uuid, "singles") != null
                        val inFree2v2Queue = CobblemonRanked.matchmakingQueue.getPlayer(player.uuid, "doubles") != null
                        val inDuo2v2Queue = DuoMatchmakingQueue.isInQueue(player.uuid)

                        when {
                            inDuo2v2Queue -> {
                                player.sendMessage(Component.literal(MessageConfig.get("status.2v2.singles", lang)))
                            }

                            inFree2v2Queue -> {
                                player.sendMessage(Component.literal(
                                    MessageConfig.get("status.2v2.solo", lang)
                                ))
                            }

                            in1v1Queue -> {
                                player.sendMessage(Component.literal(
                                    MessageConfig.get("status.1v1", lang)
                                ))
                            }

                            else -> {
                                player.sendMessage(Component.literal(
                                    MessageConfig.get("status.none", lang)
                                ))
                            }
                        }
                        1
                    }
                )
                .then(Commands.literal("setseasonname")
                    .then(Commands.argument("seasonId", IntegerArgumentType.integer(1))
                        .then(Commands.argument("name", StringArgumentType.string())
                            .requires { it.hasPermissionLevel(4) }
                            .executes { ctx ->
                                val seasonId = IntegerArgumentType.getInteger(ctx, "seasonId")
                                val name = StringArgumentType.getString(ctx, "name")
                                setSeasonName(ctx.source, seasonId, name)
                                1
                            }
                        )
                    )
                )
        )
    }

    fun setSeasonName(source: CommandSourceStack, seasonId: Int, name: String) {
        val manager = CobblemonRanked.seasonManager
        val lang = CobblemonRanked.config.defaultLang

        val season = manager.rankDao.getSeasonInfo(seasonId)
            ?: run {
                source.sendMessage(Component.literal(MessageConfig.get("setSeasonName.error", lang, "seasonId" to seasonId)))
                return
            }

        manager.rankDao.saveSeasonInfo(
            seasonId = seasonId,
            startDate = season.startDate,
            endDate = season.endDate,
            ended = season.ended,
            name = name
        )

        if (seasonId == manager.currentSeasonId) {
            manager.currentSeasonName = name
        }

        source.sendMessage(Component.literal(MessageConfig.get("setSeasonName.success", lang, "seasonId" to seasonId, "name" to name)))
    }

    private fun validateFormatOrFail(format: String, source: CommandSourceStack): Boolean {
        val lang = CobblemonRanked.config.defaultLang
        if (!CobblemonRanked.config.allowedFormats.contains(format)) {
            source.sendMessage(Component.literal(MessageConfig.get("format.invalid", lang, "format" to format)))
            return false
        }
        return true
    }

    private fun grantRankReward(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val lang = CobblemonRanked.config.defaultLang
        val player = EntityArgument.getPlayer(ctx, "player")
        val format = FormatArgumentType.getFormat(ctx, "format")
        val rank = StringArgumentType.getString(ctx, "rank")

        val validRanks = CobblemonRanked.config.rankTitles.values.toSet()
        val matchedRank = validRanks.firstOrNull { it.equals(rank.trim(), ignoreCase = true) }

        if (matchedRank == null) {
            source.sendMessage(Component.literal(MessageConfig.get("reward.invalid_rank", lang, "rank" to rank)))
            source.sendMessage(Component.literal(MessageConfig.get("reward.valid_ranks", lang, "ranks" to validRanks.joinToString())))
            return 0
        }

        return if (CobblemonRanked.rewardManager.grantRankReward(player, matchedRank, format, source.server)) {
            source.sendMessage(Component.literal(MessageConfig.get("reward.granted_to", lang, "player" to player.name.string, "format" to format, "rank" to matchedRank)))
            1
        } else {
            source.sendMessage(Component.literal(MessageConfig.get("reward.not_configured", lang)))
            0
        }
    }

    private fun endSeason(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val lang = CobblemonRanked.config.defaultLang
        if (!source.hasPermissionLevel(4)) {
            source.sendMessage(Component.literal(MessageConfig.get("permission.denied", lang)))
            return 0
        }

        CobblemonRanked.seasonManager.endSeason(source.server)
        source.sendMessage(Component.literal(MessageConfig.get("season.ended", lang)))
        return 1
    }

    private fun joinQueue(ctx: CommandContext<CommandSourceStack>, format: String?): Int {
        val player = ctx.source.player ?: return 0
        val selectedFormat = format ?: CobblemonRanked.config.defaultFormat

        CobblemonRanked.matchmakingQueue.removePlayer(player.uuid)
        DuoMatchmakingQueue.removePlayer(player)

        CobblemonRanked.matchmakingQueue.addPlayer(player, selectedFormat)
        return 1
    }

    private fun showPlayerRank(
        ctx: CommandContext<CommandSourceStack>,
        player: ServerPlayer,
        format: String,
        seasonId: Int
    ): Int {
        val lang = CobblemonRanked.config.defaultLang
        val dao = CobblemonRanked.rankDao
        val data = dao.getPlayerData(player.uuid, seasonId, format)

        if (data == null) {
            ctx.source.sendMessage(Component.literal(MessageConfig.get("rank.none", lang, "format" to format, "season" to seasonId.toString())))
            return 1
        }

        val rank = dao.getPlayerRank(player.uuid, seasonId, format)
        val rankString = if (rank != -1) "#$rank" else MessageConfig.get("rank.unranked", lang)
        val seasonName = CobblemonRanked.seasonManager.getSeasonName(seasonId)

        val msg = MessageConfig.get("rank.summary", lang,
            "player" to player.name.string,
            "format" to format,
            "season" to seasonId.toString(),
            "name" to seasonName,
            "title" to data.getRankTitle(),
            "elo" to data.elo.toString(),
            "rank" to rankString,
            "wins" to data.wins.toString(),
            "losses" to data.losses.toString(),
            "rate" to String.format("%.2f", data.winRate),
            "streak" to data.winStreak.toString(),
            "best" to data.bestWinStreak.toString(),
            "flee" to data.fleeCount.toString()
        )

        ctx.source.sendMessage(Component.literal(msg.replace("\\n", "\n")))
        return 1
    }

    private fun showLeaderboard(ctx: CommandContext<CommandSourceStack>, format: String, seasonId: Int, page: Int, count: Int): Int {
        val lang = CobblemonRanked.config.defaultLang
        val source = ctx.source
        val dao = CobblemonRanked.rankDao
        val seasonName = CobblemonRanked.seasonManager.getSeasonName(seasonId)
        val totalPlayers = dao.getPlayerCount(seasonId, format)

        if (totalPlayers == 0) {
            source.sendMessage(Component.literal(MessageConfig.get("leaderboard.empty", lang,
                "season" to seasonId.toString(),
                "name" to seasonName,
                "format" to format)))
            return 1
        }

        val pageSize = count
        val totalPages = (totalPlayers + pageSize - 1) / pageSize
        val currentPage = page.coerceIn(1, totalPages)
        val offset = (currentPage - 1).toLong() * pageSize
        val pageData = dao.getLeaderboard(seasonId, format, offset, pageSize)

        val header = MessageConfig.get("leaderboard.header", lang,
            "format" to format,
            "season" to seasonId.toString(),
            "name" to seasonName,
            "page" to currentPage.toString(),
            "total" to totalPages.toString()
        )
        source.sendMessage(Component.literal(header))

        pageData.forEachIndexed { index, data ->
            val rank = (offset + index + 1).toString()
            val entry = MessageConfig.get("leaderboard.entry", lang,
                "rank" to rank,
                "name" to data.playerName,
                "elo" to data.elo.toString(),
                "wins" to data.wins.toString(),
                "losses" to data.losses.toString(),
                "flee" to data.fleeCount.toString()
            )
            source.sendMessage(Component.literal(entry))
        }

        if (totalPages > 1) {
            val nav = Component.empty()
            if (currentPage > 1) {
                nav.append(link(MessageConfig.get("leaderboard.prev_page", lang), "/rank top $format $seasonId ${currentPage - 1} $count"))
            }

            if (currentPage < totalPages) {
                nav.append(Component.literal("   "))
                nav.append(link(MessageConfig.get("leaderboard.next_page", lang), "/rank top $format $seasonId ${currentPage + 1} $count"))
            }

            if (!nav.siblings.isEmpty()) {
                source.sendMessage(nav)
            }
        }

        return 1
    }

    private fun showSeasonInfo(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val lang = CobblemonRanked.config.defaultLang
        val season = CobblemonRanked.seasonManager
        val remaining = season.getRemainingTime()
        val seasonId = season.currentSeasonId

        val formats = CobblemonRanked.config.allowedFormats
        val participationByFormat = formats.associate { format ->
            val count = CobblemonRanked.rankDao.getParticipationCount(seasonId, format)
            format to count
        }

        val playersText = formats.joinToString(" ") { format ->
            val count = participationByFormat[format] ?: 0
            val formatName = RankUtils.getFormatDisplayName(format, lang)
            "§a$formatName: §f$count"
        }

        val message = MessageConfig.get("season.info2", lang,
            "season" to season.currentSeasonId.toString(),
            "name" to season.currentSeasonName,
            "start" to season.formatDate(season.startDate),
            "end" to season.formatDate(season.endDate),
            "duration" to CobblemonRanked.config.seasonDuration.toString(),
            "remaining" to remaining.toString(),
            "players" to playersText
        )

        source.sendMessage(Component.literal(message.replace("\\n", "\n")))
        return 1
    }

    private fun resetPlayerRank(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val lang = CobblemonRanked.config.defaultLang
        if (!source.hasPermissionLevel(4)) {
            source.sendMessage(Component.literal(MessageConfig.get("permission.denied", lang)))
            return 0
        }

        val player = EntityArgument.getPlayer(ctx, "player")
        val format = StringArgumentType.getString(ctx, "format")
        val seasonId = CobblemonRanked.seasonManager.currentSeasonId

        val success = CobblemonRanked.rankDao.deletePlayerData(player.uuid, seasonId, format)

        return if (success) {
            source.sendMessage(Component.literal(MessageConfig.get("rank.reset.success", lang, "player" to player.name.string, "format" to format)))
            1
        } else {
            source.sendMessage(Component.literal(MessageConfig.get("rank.reset.fail", lang, "format" to format)))
            0
        }
    }

    private fun showRankGui(player: ServerPlayer) {
        val lang = CobblemonRanked.config.defaultLang
        val isOp = player.hasPermissionLevel(4)

        player.sendMessage(Component.literal(MessageConfig.get("gui.main_title", lang)))

        val row1 = Component.empty()
            .append(link(MessageConfig.get("gui.my_info", lang), "/rank gui_myinfo"))
            .append(space())
            .append(link(MessageConfig.get("gui.season_info", lang), "/rank season"))
            .append(space())
            .append(link(MessageConfig.get("gui.rank_info", lang), "/rank gui_info_players"))
            .append(space())
            .append(link(MessageConfig.get("gui.leaderboard", lang), "/rank gui_top"))

        val row2 = Component.empty()
            .append(link(MessageConfig.get("gui.queue_join", lang), "/rank gui_queue"))
            .append(space())
            .append(link(MessageConfig.get("gui.status", lang), "/rank status"))
            .append(space())
            .append(link(MessageConfig.get("gui.queue_leave", lang), "/rank queue leave"))
            .append(space())
            .append(link(MessageConfig.get("pokemon_usage.statistics", lang), "/rank pokemon_usage"))

        val row3 = Component.empty()
            .append(link(MessageConfig.get("gui.cross_join_singles", lang), "/rank cross join singles"))
            .append(space())
            .append(link(MessageConfig.get("gui.cross_leave", lang), "/rank cross leave"))

        player.sendMessage(row1)
        player.sendMessage(row2)
        if (CobblemonRanked.config.enableCrossServer) {
            player.sendMessage(row3)
        }

        if (isOp) {
            val opRow = Component.empty()
                .append(link(MessageConfig.get("gui.op.reward", lang), "/rank gui_reward"))
                .append(space())
                .append(link(MessageConfig.get("gui.op.season_end", lang), "/rank season end"))
                .append(space())
                .append(link(MessageConfig.get("gui.op.reload", lang), "/rank reload"))
                .append(space())
                .append(link(MessageConfig.get("gui.op.reset", lang), "/rank gui_reset"))

            val opRow2 = Component.empty()
                .append(link(MessageConfig.get("gui.op.cross_start", lang), "/rank cross start"))
                .append(space())
                .append(link(MessageConfig.get("gui.op.cross_stop", lang), "/rank cross stop"))

            player.sendMessage(Component.literal(MessageConfig.get("gui.op.title", lang)))
            player.sendMessage(opRow)
            if (CobblemonRanked.config.enableCrossServer) {
                player.sendMessage(opRow2)
            }
        }
    }

    private fun showTopMenu(player: ServerPlayer) {
        val lang = CobblemonRanked.config.defaultLang
        val current = CobblemonRanked.seasonManager.currentSeasonId
        player.sendMessage(Component.literal(MessageConfig.get("gui.top_title", lang)))

        for (season in current downTo maxOf(1, current - 4)) {
            val row = Component.empty()
                .append(link(MessageConfig.get("gui.top.1v1", lang, "season" to season.toString()), "/rank top singles $season"))
                .append(space())
                .append(link(MessageConfig.get("gui.top.2v2", lang, "season" to season.toString()), "/rank top doubles $season"))
                .append(space())
                .append(link(MessageConfig.get("gui.top.2v2singles", lang, "season" to season.toString()), "/rank top 2v2singles $season"))
            player.sendMessage(row)
        }
    }

    private fun showInfoMenu(player: ServerPlayer) {
        val lang = CobblemonRanked.config.defaultLang
        val current = CobblemonRanked.seasonManager.currentSeasonId
        player.sendMessage(Component.literal(MessageConfig.get("gui.info_title", lang)))

        for (season in current downTo maxOf(1, current - 4)) {
            val row = Component.empty()
                .append(link(MessageConfig.get("gui.info.1v1", lang, "season" to season.toString(), "player" to player.name.string), "/rank info ${player.name.string} singles $season"))
                .append(space())
                .append(link(MessageConfig.get("gui.info.2v2", lang, "season" to season.toString(), "player" to player.name.string), "/rank info ${player.name.string} doubles $season"))
                .append(space())
                .append(link(MessageConfig.get("gui.info.2v2singles", lang, "season" to season.toString(), "player" to player.name.string), "/rank info ${player.name.string} 2v2singles $season"))
            player.sendMessage(row)
        }
    }

    private fun showQueueMenu(player: ServerPlayer) {
        val lang = CobblemonRanked.config.defaultLang
        player.sendMessage(Component.literal(MessageConfig.get("gui.queue_title", lang)))
        player.sendMessage(
            Component.empty()
                .append(link(MessageConfig.get("gui.queue.1v1", lang), "/rank queue join singles"))
                .append(space())
                .append(link(MessageConfig.get("gui.queue.2v2", lang), "/rank queue join doubles"))
                .append(space())
                .append(link(MessageConfig.get("gui.queue.2v2singles", lang), "/rank queue join 2v2singles"))
        )
    }

    private fun showRewardFormatMenu(player: ServerPlayer) {
        val lang = CobblemonRanked.config.defaultLang
        val formats = CobblemonRanked.config.allowedFormats

        val ranks = CobblemonRanked.config.rankTitles.entries
            .sortedByDescending { it.key }

        player.sendMessage(Component.literal(MessageConfig.get("gui.reward.top", lang)))

        for (format in formats) {
            player.sendMessage(Component.literal(MessageConfig.get("gui.reward.title", lang, "format" to format)))

            for ((elo, rankName) in ranks) {
                val row = link(
                    MessageConfig.get("gui.reward.claim", lang, "rank" to rankName),
                    "/rank reward ${player.name.string} $format $rankName"
                )
                player.sendMessage(row)
            }
        }
    }

    private fun showResetPlayerList(player: ServerPlayer, page: Int) {
        val lang = CobblemonRanked.config.defaultLang
        if (!player.hasPermissionLevel(4)) {
            player.sendMessage(Component.literal(MessageConfig.get("permission.denied", lang)))
            return
        }

        val allPlayers = player.server.playerManager.playerList
        val perPage = 20
        val start = (page - 1) * perPage
        val end = minOf(start + perPage, allPlayers.size)
        val totalPages = (allPlayers.size + perPage - 1) / perPage

        player.sendMessage(Component.literal(MessageConfig.get("gui.reset.title", lang, "page" to page.toString(), "total" to totalPages.toString())))

        for (i in start until end) {
            val target = allPlayers[i]
            val name = target.name.string
            player.sendMessage(Component.empty()
                .append(link(MessageConfig.get("gui.reset.1v1", lang), "/rank reset $name singles"))
                .append(space())
                .append(link(MessageConfig.get("gui.reset.2v2", lang), "/rank reset $name doubles"))
                .append(space())
                .append(link(MessageConfig.get("gui.reset.2v2singles", lang), "/rank reset $name 2v2singles"))
                .append(space())
                .append(Component.literal("§f$name"))
            )
        }

        val nav = Component.empty()
        if (page > 1) nav.append(link(MessageConfig.get("gui.invite.prev", lang), "/rank gui_reset ${page - 1}")).append(space())
        if (end < allPlayers.size) nav.append(link(MessageConfig.get("gui.invite.next", lang), "/rank gui_reset ${page + 1}"))
        if (!nav.siblings.isEmpty()) player.sendMessage(nav)

        player.sendMessage(Component.literal(MessageConfig.get("gui.reset.tip", lang)))
    }

    private fun showInfoPlayerMenu(player: ServerPlayer, page: Int) {
        val lang = CobblemonRanked.config.defaultLang
        val allPlayers = player.server.playerManager.playerList
        val perPage = 20
        val start = (page - 1) * perPage
        val end = minOf(start + perPage, allPlayers.size)
        val totalPages = (allPlayers.size + perPage - 1) / perPage

        player.sendMessage(Component.literal(MessageConfig.get("gui.info_player.title", lang, "page" to page.toString(), "total" to totalPages.toString())))

        for (i in start until end) {
            val p = allPlayers[i]
            player.sendMessage(Component.empty()
                .append(link(MessageConfig.get("gui.info_player.1v1", lang), "/rank gui_info_format ${p.name.string} singles"))
                .append(space())
                .append(link(MessageConfig.get("gui.info_player.2v2", lang), "/rank gui_info_format ${p.name.string} doubles"))
                .append(space())
                .append(link(MessageConfig.get("gui.info_player.2v2singles", lang), "/rank gui_info_format ${p.name.string} 2v2singles"))
                .append(space())
                .append(Component.literal("§f${p.name.string}"))
            )
        }

        val nav = Component.empty()
        if (page > 1) nav.append(link(MessageConfig.get("gui.invite.prev", lang), "/rank gui_info_players ${page - 1}")).append(space())
        if (end < allPlayers.size) nav.append(link(MessageConfig.get("gui.invite.next", lang), "/rank gui_info_players ${page + 1}"))
        if (!nav.siblings.isEmpty()) player.sendMessage(nav)
    }

    private fun showInfoMenuForPlayer(requester: ServerPlayer?, target: ServerPlayer, format: String, season: Int) {
        val lang = CobblemonRanked.config.defaultLang
        requester?.sendMessage(Component.literal(MessageConfig.get("gui.info_target.title", lang, "player" to target.name.string, "format" to format)))
        for (s in season downTo maxOf(1, season - 4)) {
            val line = link(MessageConfig.get("gui.info_target.season", lang, "season" to s.toString()), "/rank info ${target.name.string} $format $s")
            requester?.sendMessage(line)
        }
    }

    private fun showMyInfoMenu(player: ServerPlayer, season: Int) {
        val lang = CobblemonRanked.config.defaultLang
        for (s in season downTo maxOf(1, season - 4)) {
            val seasonName = CobblemonRanked.seasonManager.getSeasonName(s)
            val row1 = Component.empty()
                .append(MessageConfig.get("gui.info_target.season", lang, "season" to s.toString(), "name" to seasonName))
                .append(space())
                .append(link(MessageConfig.get("gui.myinfo.1v1", lang), "/rank info ${player.name.string} singles $s"))
                .append(space())
                .append(link(MessageConfig.get("gui.myinfo.2v2", lang), "/rank info ${player.name.string} doubles $s"))
                .append(space())
                .append(link(MessageConfig.get("gui.myinfo.2v2singles", lang), "/rank info ${player.name.string} 2v2singles $s"))
            player.sendMessage(row1)
        }
    }

    private fun link(label: String, command: String, hoverKey: String? = null): Component {
        val lang = CobblemonRanked.config.defaultLang
        val hoverText = hoverKey?.let {
            MessageConfig.get(it, lang, "command" to command)
        } ?: MessageConfig.get(
            "command.hint",
            lang,
            "command" to command
        )

        return Component.literal(label).setStyle(
            Style.EMPTY
                .withClickEvent(net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)))
        )
    }

    private fun showPokemonUsage(ctx: CommandContext<CommandSourceStack>, seasonId: Int, page: Int): Int {
        val source = ctx.source
        val lang = CobblemonRanked.config.defaultLang
        val dao = CobblemonRanked.rankDao
        val seasonName = CobblemonRanked.seasonManager.getSeasonName(seasonId)

        val pageSize = 10
        val offset = (page - 1) * pageSize
        val usageList = dao.getPokemonUsage(seasonId, pageSize, offset)
        val total = dao.getTotalPokemonUsage(seasonId)
        val totalPages = (total + pageSize - 1) / pageSize

        if (usageList.isEmpty()) {
            source.sendMessage(Component.literal(MessageConfig.get("pokemon_usage.empty", lang, "season" to seasonId.toString(), "name" to seasonName)))
            return 1
        }

        val totalUsageCount = dao.getTotalPokemonUsageCount(seasonId)

        val header = MessageConfig.get("pokemon_usage.header", lang,
            "season" to seasonId.toString(),
            "page" to page.toString(),
            "total" to totalPages.toString(),
            "name" to seasonName
        )
        source.sendMessage(Component.literal(header))

        usageList.forEachIndexed { index, (species, count) ->
            val usageRate = if (totalUsageCount > 0) {
                String.format("%.2f", count.toDouble() / totalUsageCount * 100)
            } else {
                "0.00"
            }

            val entry = MessageConfig.get("pokemon_usage.entry", lang,
                "rank" to (offset + index + 1).toString(),
                "species" to species,
                "count" to count.toString(),
                "rate" to usageRate
            )
            source.sendMessage(Component.literal(entry))
        }

        val nav = Component.empty()
        if (page > 1) {
            nav.append(link(MessageConfig.get("leaderboard.prev_page", lang), "/rank pokemon_usage $seasonId ${page - 1}"))
        }
        if (page < totalPages) {
            if (nav.siblings.isNotEmpty()) nav.append(Component.literal("   "))
            nav.append(link(MessageConfig.get("leaderboard.next_page", lang), "/rank pokemon_usage $seasonId ${page + 1}"))
        }

        if (nav.siblings.isNotEmpty()) {
            source.sendMessage(nav)
        }

        return 1
    }

    private fun space(): Component = Component.literal(" ")
}

