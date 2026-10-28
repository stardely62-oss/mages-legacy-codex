package euf.mageslegacy.test;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** With -Dmages_legacy.genworld=true: lets the dedicated server create its world, then stops it (runGenworld). */
public class GenWorldServer implements DedicatedServerModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("mages_legacy-genworld");

	@Override
	public void onInitializeServer() {
		if (!Boolean.getBoolean("mages_legacy.genworld")) {
			return;
		}
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LOGGER.info("[genworld] world created, stopping");
			server.halt(false);
		});
	}
}
