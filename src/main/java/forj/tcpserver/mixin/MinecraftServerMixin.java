package forj.tcpserver.mixin;

import com.mojang.datafixers.DataFixer;
import forj.tcpserver.TcpMixinAccessor;
import forj.tcpserver.TcpServerManager;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.SaveLoader;
import net.minecraft.server.WorldGenerationProgressListenerFactory;
import net.minecraft.util.ApiServices;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.Proxy;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin implements TcpMixinAccessor {
    @Unique private TcpServerManager tcpServer$tcpServerManager;

    @Inject(method = "<init>", at = @At("RETURN"))
    public void onInit(Thread serverThread, LevelStorage.Session session, ResourcePackManager dataPackManager, SaveLoader saveLoader, Proxy proxy, DataFixer dataFixer, ApiServices apiServices, WorldGenerationProgressListenerFactory worldGenerationProgressListenerFactory, CallbackInfo ci) {
        this.tcpServer$tcpServerManager = new TcpServerManager((MinecraftServer) (Object) this);
    }
    @Inject(method = "shutdown",  at = @At("RETURN"))
    public void tcpServer$shutdown(CallbackInfo ci) {
        this.tcpServer$tcpServerManager.stopAll();
    }

    @Override @Unique
    public TcpServerManager tcpServer$getTcpServerManager() {
        return this.tcpServer$tcpServerManager;
    }
}
