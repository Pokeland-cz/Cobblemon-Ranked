package cn.kurt6.cobblemon_ranked.client

import cn.kurt6.cobblemon_ranked.CobblemonRanked
import cn.kurt6.cobblemon_ranked.client.gui.RankedMainMenuScreen
import cn.kurt6.cobblemon_ranked.network.ClientVersionPayload
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW

object CobblemonRankedClient {
    const val CLIENT_MOD_VERSION = "1.4.2"

    private val openGuiKey = KeyMapping(
        "key.cobblemon_ranked.open_gui",
        GLFW.GLFW_KEY_X,
        "category.cobblemon_ranked"
    )

    fun registerKeyMappings(event: RegisterKeyMappingsEvent) {
        event.register(openGuiKey)
    }

    fun handleClientTick() {
        val minecraft = Minecraft.getInstance()
        while (openGuiKey.consumeClick()) {
            minecraft.setScreen(RankedMainMenuScreen())
        }
    }

    fun handleLogin() {
        PacketDistributor.sendToServer(ClientVersionPayload(CLIENT_MOD_VERSION))
    }
}

@EventBusSubscriber(modid = CobblemonRanked.MOD_ID, value = [Dist.CLIENT], bus = EventBusSubscriber.Bus.MOD)
object CobblemonRankedClientModEvents {
    @SubscribeEvent
    fun onRegisterKeyMappings(event: RegisterKeyMappingsEvent) {
        CobblemonRankedClient.registerKeyMappings(event)
    }
}

@EventBusSubscriber(modid = CobblemonRanked.MOD_ID, value = [Dist.CLIENT])
object CobblemonRankedClientForgeEvents {
    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        CobblemonRankedClient.handleClientTick()
    }

    @SubscribeEvent
    fun onPlayerLogin(event: ClientPlayerNetworkEvent.LoggingIn) {
        CobblemonRankedClient.handleLogin()
    }
}
