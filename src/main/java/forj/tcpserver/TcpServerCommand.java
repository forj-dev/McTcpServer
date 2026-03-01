package forj.tcpserver;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.CommandFunctionArgumentType;
import net.minecraft.command.argument.NbtCompoundArgumentType;
import net.minecraft.command.argument.NbtPathArgumentType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.command.DataCommand;
import net.minecraft.server.command.FunctionCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.function.CommandFunction;
import net.minecraft.text.Text;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class TcpServerCommand {
    public static final SuggestionProvider<ServerCommandSource> PORT_SUGGESTION_PROVIDER = (context, builder) -> {
        TcpServerManager manager = ((TcpMixinAccessor) context.getSource().getServer()).tcpServer$getTcpServerManager();
        Set<Integer> ports = manager.getPorts();
        return CommandSource.suggestMatching(ports.stream().map(Object::toString), builder);
    };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> literalArgumentBuilder = literal("tcpserver")
                .requires(source -> source.hasPermissionLevel(3))

                // create [port]
                .then(literal("create")
                        .then(argument("port", IntegerArgumentType.integer(1, 65535))
                                .executes(context -> createServer(context, IntegerArgumentType.getInteger(context, "port")))
                        )
                        .executes(context -> createServer(context, -1))
                )

                // list [port]
                .then(literal("list")
                        .then(argument("port", IntegerArgumentType.integer(1, 65535))
                                .suggests(PORT_SUGGESTION_PROVIDER)
                                .executes(context -> listClients(context, IntegerArgumentType.getInteger(context, "port")))
                        )
                        .executes(TcpServerCommand::listServers)
                )

                // close [port]
                .then(literal("close")
                        .then(argument("port", IntegerArgumentType.integer(1, 65535))
                                .suggests(PORT_SUGGESTION_PROVIDER)
                                .executes(context -> stopServer(context, IntegerArgumentType.getInteger(context, "port")))
                        )
                        .executes(context -> stopServer(context, -1))
                )

                // send <port> <nbt>
                .then(literal("send")
                        .then(argument("port", IntegerArgumentType.integer(1, 65535))
                                .suggests(PORT_SUGGESTION_PROVIDER)
                                .then(argument("nbt", NbtCompoundArgumentType.nbtCompound())
                                        .executes(context -> sendMsg(context,
                                                IntegerArgumentType.getInteger(context, "port"),
                                                NbtCompoundArgumentType.getNbtCompound(context, "nbt"))
                                        )
                                )
                        )
                )

                // recvfunc <port> [func]
                .then(literal("recvfunc")
                        .then(argument("port", IntegerArgumentType.integer(1, 65535))
                                .suggests(PORT_SUGGESTION_PROVIDER)
                                .then(argument("func", CommandFunctionArgumentType.commandFunction())
                                        .suggests(FunctionCommand.SUGGESTION_PROVIDER)
                                        .executes(context -> setRecvFunc(context,
                                                IntegerArgumentType.getInteger(context, "port"),
                                                CommandFunctionArgumentType.getFunctions(context, "func"))
                                        )
                                )
                                .executes(context -> clearRecvFunc(context, IntegerArgumentType.getInteger(context, "port")))
                        )
                )

                // recvpath <port>
                .then(literal("recvpath")
                        .then(argument("port", IntegerArgumentType.integer(1, 65535))
                                .suggests(PORT_SUGGESTION_PROVIDER)
                                .executes(context -> clearRecvPath(context, IntegerArgumentType.getInteger(context, "port")))
                        )
                );
        for (DataCommand.ObjectType objectType : DataCommand.TARGET_OBJECT_TYPES) {
            literalArgumentBuilder
                    // recvpath <port> <entity|block|storage> <target> <path>
                    .then(literal("recvpath")
                            .then(objectType.addArgumentsToBuilder(
                                    argument("port", IntegerArgumentType.integer(1, 65535)),
                                    command -> command.then(argument("path", NbtPathArgumentType.nbtPath())
                                            .executes(context -> setRecvPath(context,
                                                    IntegerArgumentType.getInteger(context, "port"),
                                                    objectType,
                                                    NbtPathArgumentType.getNbtPath(context, "path"))
                                            )
                                    )
                            ))
                    )

                    // send <port> <entity|block|storage> <target> <path>
                    .then(literal("send")
                            .then(objectType.addArgumentsToBuilder(
                                    argument("port", IntegerArgumentType.integer(1, 65535)),
                                    command -> command.then(argument("path", NbtPathArgumentType.nbtPath())
                                            .executes(context -> send(context,
                                                    IntegerArgumentType.getInteger(context, "port"),
                                                    objectType,
                                                    NbtPathArgumentType.getNbtPath(context, "path"))
                                            )
                                    )
                            ))
                    );
        }
        dispatcher.register(literalArgumentBuilder);
    }

    private static int createServer(CommandContext<ServerCommandSource> context, int port) throws CommandException {
        TcpServerManager manager = ((TcpMixinAccessor) context.getSource().getServer()).tcpServer$getTcpServerManager();
        if (port == -1) {
            try {
                int ret = manager.createServer();
                context.getSource().sendFeedback(Text.literal("Created server on port " + ret), false);
                return ret;
        } catch (IOException e) {
                throw new CommandException(Text.literal("No available ports."));
            }
        } else {
            try {
                manager.createServer(port);
                context.getSource().sendFeedback(Text.literal("Created server on port " + port), false);
                return port;
            } catch (IOException e) {
                throw new CommandException(Text.literal("Port already in use."));
            }
        }
    }

    private static int listServers(CommandContext<ServerCommandSource> context) {
        TcpServerManager manager = ((TcpMixinAccessor) context.getSource().getServer()).tcpServer$getTcpServerManager();
        Set<Integer> ports = manager.getPorts();
        if (ports.isEmpty()) {
            context.getSource().sendFeedback(Text.literal("No servers running"), false);
            return 0;
        }
        String portsString = ports.stream().map(Object::toString).collect(Collectors.joining(", "));
        context.getSource().sendFeedback(Text.literal("Running " + ports.size() + " servers: " + portsString), false);
        return ports.size();
    }

    private static int listClients(CommandContext<ServerCommandSource> context, int port) throws CommandException {
        TcpServerManager manager = ((TcpMixinAccessor) context.getSource().getServer()).tcpServer$getTcpServerManager();
        TcpServerInstance server = manager.getServer(port);
        if (server == null) {
            throw new CommandException(Text.literal("No server running on this port"));
        }
        List<String> clients = server.getClients();
        if (clients.isEmpty()) {
            context.getSource().sendFeedback(Text.literal("No clients connected."), false);
            return 0;
        }
        String clientsString = clients.stream().map(client -> "[" + client + "]").collect(Collectors.joining(", "));
        context.getSource().sendFeedback(Text.literal("Clients connected: " + clientsString), false);
        return clients.size();
    }

    private static int stopServer(CommandContext<ServerCommandSource> context, int port) throws CommandException {
        TcpServerManager manager = ((TcpMixinAccessor) context.getSource().getServer()).tcpServer$getTcpServerManager();
        if (!manager.hasServer(port) && port != -1) throw new CommandException(Text.literal("No server running on this port"));
        if (port == -1) {
            int count = manager.getPorts().size();
            manager.stopAll();
            context.getSource().sendFeedback(Text.literal("Stopped " + count + " servers"), false);
            return count;
        }
        manager.stopServer(port);
        context.getSource().sendFeedback(Text.literal("Stopped server on port " + port), false);
        return 1;
    }

    private static int sendMsg(CommandContext<ServerCommandSource> context, int port, NbtCompound msg) throws CommandException {
        TcpServerManager manager = ((TcpMixinAccessor) context.getSource().getServer()).tcpServer$getTcpServerManager();
        TcpServerInstance server = manager.getServer(port);
        if (server == null) throw new CommandException(Text.literal("No server running on this port"));
        int success = server.send(msg);
        context.getSource().sendFeedback(Text.literal("Sent " + success + " messages"), false);
        return success;
    }

    private static int setRecvFunc(CommandContext<ServerCommandSource> context, int port, Collection<CommandFunction> functions) throws CommandException {
        TcpServerManager manager = ((TcpMixinAccessor) context.getSource().getServer()).tcpServer$getTcpServerManager();
        TcpServerInstance server = manager.getServer(port);
        if (server == null) throw new CommandException(Text.literal("No server running on this port"));
        if (functions.size() != 1)
            throw new CommandException(Text.literal("Only one function is supported for callbacks"));
        server.setRecvCallback(functions.iterator().next());
        context.getSource().sendFeedback(Text.literal("Set callback for server on port " + port), false);
        return 1;
    }

    private static int clearRecvFunc(CommandContext<ServerCommandSource> context, int port) throws CommandException {
        TcpServerManager manager = ((TcpMixinAccessor) context.getSource().getServer()).tcpServer$getTcpServerManager();
        TcpServerInstance server = manager.getServer(port);
        if (server == null) throw new CommandException(Text.literal("No server running on this port"));
        server.clearRecvCallback();
        context.getSource().sendFeedback(Text.literal("Cleared callback for server on port " + port), false);
        return 1;
    }

    private static int setRecvPath(CommandContext<ServerCommandSource> context, int port, DataCommand.ObjectType objectType, NbtPathArgumentType.NbtPath path) throws CommandException {
        TcpServerManager manager = ((TcpMixinAccessor) context.getSource().getServer()).tcpServer$getTcpServerManager();
        TcpServerInstance server = manager.getServer(port);
        if (server == null) throw new CommandException(Text.literal("No server running on this port"));
        try {
            server.setRecvPath(objectType.getObject(context), path);
            context.getSource().sendFeedback(Text.literal("Set path for server on port " + port), false);
            return 1;
        } catch (CommandSyntaxException e) {
            throw new CommandException(Text.literal("Invalid path"));
        }
    }

    private static int clearRecvPath(CommandContext<ServerCommandSource> context, int port) throws CommandException {
        TcpServerManager manager = ((TcpMixinAccessor) context.getSource().getServer()).tcpServer$getTcpServerManager();
        TcpServerInstance server = manager.getServer(port);
        if (server == null) throw new CommandException(Text.literal("No server running on this port"));
        server.clearRecvPath();
        context.getSource().sendFeedback(Text.literal("Cleared path for server on port " + port), false);
        return 1;
    }

    private static int send(CommandContext<ServerCommandSource> context, int port, DataCommand.ObjectType objectType, NbtPathArgumentType.NbtPath path) throws CommandException {
        TcpServerManager manager = ((TcpMixinAccessor) context.getSource().getServer()).tcpServer$getTcpServerManager();
        TcpServerInstance server = manager.getServer(port);
        if (server == null) throw new CommandException(Text.literal("No server running on this port"));

        try {
            List<NbtElement> msgElements = path.get(objectType.getObject(context).getNbt());
            if (msgElements.size() != 1) throw new CommandException(Text.literal("Only one element is supported"));
            NbtElement msgElement = msgElements.get(0);
            if (!(msgElement instanceof NbtCompound msg))
                throw new CommandException(Text.literal("Invalid object type"));
            int success = server.send(msg);
            context.getSource().sendFeedback(Text.literal("Sent " + success + " messages"), false);
            return success;
        } catch (CommandSyntaxException e) {
            throw new CommandException(Text.literal("Invalid path"));
        }
    }
}
