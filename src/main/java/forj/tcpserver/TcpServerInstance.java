package forj.tcpserver;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.DataCommandObject;
import net.minecraft.command.argument.NbtPathArgumentType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.function.CommandFunction;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TcpServerInstance {
    private final int port;
    private final MinecraftServer server;
    private volatile DataCommandObject recvObject;
    private volatile NbtPathArgumentType.NbtPath recvPath;
    private volatile CommandFunction recvCallback;
    private volatile boolean stopped;

    private final ServerSocket serverSocket;
    private final ExecutorService threadPool;
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    public TcpServerInstance(MinecraftServer server, int port) throws IOException {
        this.server = server;
        this.port = port;
        this.recvObject = null;
        this.recvPath = null;
        this.recvCallback = null;
        this.stopped = false;
        this.serverSocket = new ServerSocket(port);
        this.threadPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "TCPServer-Port" + port);
            t.setDaemon(true);
            return t;
        });
        threadPool.submit(this::acceptConnections);
        TCPServer.LOGGER.info("Started TCP server on port " + port);
    }

    private void acceptConnections() {
        while (!stopped) {
            try {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket);
                clients.add(handler);
                threadPool.submit(handler);
            } catch (IOException e) {
                if (!stopped) {
                    TCPServer.LOGGER.warn("Error accepting connection on port " + port, e);
                }
            }
        }
    }

    public void stop() {
        this.stopped = true;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            TCPServer.LOGGER.error("Error closing server socket", e);
        }
        for (ClientHandler client : clients) {
            try {
                client.close();
            } catch (Exception e) {
                TCPServer.LOGGER.error("Error closing client socket", e);
            }
        }
        clients.clear();
        if (threadPool != null) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
            }
        }
        TCPServer.LOGGER.info("Stopped TCP server on port {}", port);
    }

    public void setRecvPath(DataCommandObject recvObj, NbtPathArgumentType.NbtPath recvPath) {
        this.recvObject = recvObj;
        this.recvPath = recvPath;
    }

    public void setRecvCallback(CommandFunction recvCallback) {
        this.recvCallback = recvCallback;
    }

    public void clearRecvPath() {
        this.recvObject = null;
        this.recvPath = null;
    }

    public void clearRecvCallback() {
        this.recvCallback = null;
    }

    public List<String> getClients() {
        return clients.stream().map(ClientHandler::getAddress).toList();
    }

    private void onRecvMsg(String msg) {
        try {
            NbtCompound nbt = StringNbtReader.parse(msg);
            //volatile
            DataCommandObject obj = this.recvObject;
            CommandFunction callback = this.recvCallback;
            if (obj != null || callback != null) {
                server.execute(() -> {
                    DataCommandObject currentObject = this.recvObject;
                    NbtPathArgumentType.NbtPath currentPath = this.recvPath;
                    CommandFunction currentCallback = this.recvCallback;
                    if (currentObject != null) {
                        try {
                            NbtCompound objNbt = currentObject.getNbt();
                            currentPath.put(objNbt, nbt);
                            currentObject.setNbt(objNbt);
                        } catch (CommandSyntaxException ignored) {
                        }
                    }
                    if (currentCallback != null) {
                        server.getCommandFunctionManager().execute(currentCallback,
                                server.getCommandSource().withSilent().withLevel(3));
                    }
                });
            }
        } catch (CommandSyntaxException e) {
            TCPServer.LOGGER.warn("Received non-SNBT data on port {}, discarding.", this.port);
        }
    }

    public int send(NbtCompound nbt) {
        if (this.stopped) throw new IllegalStateException("This TCP Server is stopped");
        String msg = nbt.asString();
        int count = 0;
        for (ClientHandler client : clients) {
            try {
                client.send(msg);
                count++;
            } catch (Exception e) {
                client.close();
            }
        }
        return count;
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private final PrintWriter out;
        private final BufferedReader in;

        public ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        }

        @Override
        public void run() {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    onRecvMsg(line);
                }
                TCPServer.LOGGER.info("Client disconnected: {}", getAddress());
            } catch (IOException e) {
                TCPServer.LOGGER.warn("Error reading from client: " + e.getMessage());
                if (!stopped) {
                    TCPServer.LOGGER.warn("Client disconnected abruptly: " + e.getMessage());
                }
            } finally {
                close();
            }
        }

        public void send(String msg) {
            out.println(msg);
            out.flush();
        }

        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
            } finally {
                clients.remove(this);
            }
        }

        public String getAddress() {
            return socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        }
    }
}