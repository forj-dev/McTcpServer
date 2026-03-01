package forj.tcpserver;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TcpServerManager {
    private final Map<Integer, TcpServerInstance> servers = new HashMap<>();
    private final MinecraftServer server;

    public TcpServerManager(MinecraftServer server) {
        this.server = server;
    }

    public void createServer(int port) throws IOException {
        TcpServerInstance server = new TcpServerInstance(this.server, port);
        servers.put(port, server);
    }

    public int createServer() throws IOException {
        for (int i = 10001; i <= 65535; i++) {
            try {
                createServer(i);
                return i;
            } catch (IOException ignored) {
            }
        }
        throw new IOException("No available port found");
    }

    @Nullable
    public TcpServerInstance getServer(int port) {
        return servers.get(port);
    }

    public boolean hasServer(int port) {
        return servers.containsKey(port);
    }

    public void stopServer(int port) {
        TcpServerInstance server = servers.get(port);
        if (server != null) {
            server.stop();
            servers.remove(port);
        }
    }

    public void stopAll() {
        for (TcpServerInstance server : servers.values()) {
            server.stop();
        }
        servers.clear();
    }

    public Set<Integer> getPorts() {
        return servers.keySet();
    }
}
