package cn.kurt6.cobblemon_ranked.battle

import net.minecraft.server.MinecraftServer

internal fun scheduleServerActionImpl(delayMillis: Long, action: (MinecraftServer) -> Unit) {
    BattleHandler.pendingServerActions.add(
        BattleHandler.PendingServerAction(
            executeAtMillis = System.currentTimeMillis() + delayMillis,
            action = action
        )
    )
}

internal fun processPendingServerActionsImpl(server: MinecraftServer) {
    if (BattleHandler.pendingServerActions.isEmpty()) {
        return
    }

    val now = System.currentTimeMillis()
    val dueActions = mutableListOf<BattleHandler.PendingServerAction>()
    val deferredActions = mutableListOf<BattleHandler.PendingServerAction>()

    while (true) {
        val action = BattleHandler.pendingServerActions.poll() ?: break
        if (action.executeAtMillis <= now) {
            dueActions += action
        } else {
            deferredActions += action
        }
    }

    deferredActions.forEach(BattleHandler.pendingServerActions::add)
    dueActions.forEach { pending ->
        try {
            pending.action(server)
        } catch (e: Exception) {
            BattleHandler.logger.error("Error executing delayed server action", e)
        }
    }
}
