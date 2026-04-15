package cn.kurt6.cobblemon_ranked.client.network

import cn.kurt6.cobblemon_ranked.client.gui.ModeScreen
import cn.kurt6.cobblemon_ranked.client.gui.TeamSelectionScreen
import cn.kurt6.cobblemon_ranked.network.LeaderboardPayload
import cn.kurt6.cobblemon_ranked.network.PlayerRankDataPayload
import cn.kurt6.cobblemon_ranked.network.RequestType
import cn.kurt6.cobblemon_ranked.network.SeasonInfoTextPayload
import cn.kurt6.cobblemon_ranked.network.TeamSelectionEndPayload
import cn.kurt6.cobblemon_ranked.network.TeamSelectionStartPayload
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object ClientNetworking {
    fun handlePlayerRank(payload: PlayerRankDataPayload) {
        Minecraft.getInstance().execute {
            val total = payload.wins + payload.losses
            val rate = if (total == 0) 0.0 else payload.wins.toDouble() / total * 100
            val rankStr = payload.globalRank?.let { "#$it" } ?: Component.translatable("cobblemon_ranked.not_ranked").string

            val info = buildString {
                append(Component.translatable("cobblemon_ranked.info.title", payload.playerName, ModeScreen.modeName(payload.format).string, payload.seasonId).string)
                append('\n')
                append(Component.translatable("cobblemon_ranked.info.rank", payload.rankTitle, payload.elo).string)
                append('\n')
                append(Component.translatable("cobblemon_ranked.info.global_rank", rankStr).string)
                append('\n')
                append(Component.translatable("cobblemon_ranked.info.record", payload.wins, payload.losses, rate).string)
                append('\n')
                append(Component.translatable("cobblemon_ranked.info.streak", payload.winStreak, payload.bestWinStreak).string)
                append('\n')
                append(Component.translatable("cobblemon_ranked.info.flee", payload.fleeCount).string)
            }

            updateModeScreen(RequestType.PLAYER, info)
        }
    }

    fun handleSeasonInfo(payload: SeasonInfoTextPayload) {
        Minecraft.getInstance().execute {
            updateModeScreen(RequestType.SEASON, payload.text)
        }
    }

    fun handleLeaderboard(payload: LeaderboardPayload) {
        Minecraft.getInstance().execute {
            updateModeScreen(RequestType.LEADERBOARD, payload.text)
        }
    }

    fun handleTeamSelectionStart(payload: TeamSelectionStartPayload) {
        Minecraft.getInstance().execute {
            Minecraft.getInstance().setScreen(
                TeamSelectionScreen(
                    payload.limit,
                    payload.timeLimitSeconds,
                    payload.opponentName,
                    payload.opponentTeam,
                    payload.yourTeam
                )
            )
        }
    }

    fun handleTeamSelectionEnd(@Suppress("UNUSED_PARAMETER") payload: TeamSelectionEndPayload) {
        Minecraft.getInstance().execute {
            val minecraft = Minecraft.getInstance()
            if (minecraft.screen is TeamSelectionScreen) {
                minecraft.screen?.onClose()
            }
        }
    }

    private fun updateModeScreen(type: RequestType, text: String) {
        val screen = Minecraft.getInstance().screen
        if (screen is ModeScreen) {
            screen.updateInfo(type, text)
        }
    }
}
