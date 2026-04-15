package cn.kurt6.cobblemon_ranked.mixin

import cn.kurt6.cobblemon_ranked.NeoForgeDisconnectBridge
import net.minecraft.network.DisconnectionDetails
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ServerGamePacketListenerImpl::class)
abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    lateinit var player: ServerPlayer

    @Inject(method = ["onDisconnect"], at = [At("HEAD")])
    private fun handleEarlyDisconnect(details: DisconnectionDetails, ci: CallbackInfo) {
        NeoForgeDisconnectBridge.handleDisconnect(player)
    }
}
