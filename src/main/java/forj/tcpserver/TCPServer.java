package forj.tcpserver;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TCPServer implements ModInitializer {
	public static final String MOD_ID = "tcp-server";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing TCP Server Mod");
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				TcpServerCommand.register(dispatcher));
		LOGGER.info("Initialized TCP Server Mod");
	}
}