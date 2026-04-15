package cn.kurt6.cobblemon_ranked.util

import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.players.PlayerList
import net.minecraft.world.entity.player.Player
import java.util.UUID

fun Component.formatted(formatting: ChatFormatting): Component = copy().withStyle(formatting)

fun Player.sendMessage(message: Component) {
    displayClientMessage(message, false)
}

fun Player.sendMessage(message: Component, overlay: Boolean) {
    displayClientMessage(message, overlay)
}

fun Component.styled(styleUpdater: (Style) -> Style): MutableComponent = copy().withStyle(styleUpdater)

fun CommandSourceStack.hasPermissionLevel(level: Int): Boolean = hasPermission(level)

fun ServerPlayer.hasPermissionLevel(level: Int): Boolean = hasPermissions(level)

val MinecraftServer.playerManager
    get() = playerList

val MinecraftServer.commandManager
    get() = commands

val MinecraftServer.commandSource
    get() = createCommandSourceStack()

val CommandSourceStack.player: ServerPlayer?
    get() = try {
        playerOrException
    } catch (_: Exception) {
        null
    }

fun CommandSourceStack.sendMessage(message: Component) {
    source.sendSystemMessage(message)
}

fun CommandSourceStack.sendSuccess(message: String, broadcastToOps: Boolean = false) {
    sendSuccess({ Component.literal(message) }, broadcastToOps)
}

val PlayerList.playerList: List<ServerPlayer>
    get() = players

fun PlayerList.getPlayer(name: String): ServerPlayer? = getPlayerByName(name)

fun PlayerList.broadcast(message: Component, overlay: Boolean) {
    broadcastSystemMessage(message, overlay)
}

val ServerPlayer.serverWorld: ServerLevel
    get() = serverLevel()

val ServerPlayer.isDisconnected: Boolean
    get() = hasDisconnected()

val ServerPlayer.commandTags: Set<String>
    get() = tags

fun ServerPlayer.addCommandTag(tag: String): Boolean = addTag(tag)

fun ServerPlayer.removeCommandTag(tag: String): Boolean = removeTag(tag)

val ServerPlayer.uuidAsString: String
    get() = uuid.toString()

fun ServerPlayer.teleport(world: ServerLevel, x: Double, y: Double, z: Double, yaw: Float, pitch: Float) {
    teleportTo(world, x, y, z, yaw, pitch)
}

val ServerLevel.registryKey
    get() = dimension()
