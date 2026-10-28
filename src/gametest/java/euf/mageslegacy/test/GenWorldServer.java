package euf.mageslegacy.test;


import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** With -Dmages_legacy.genworld=true: lets the dedicated server create its world, then stops it (runGenworld). */
public class GenWorldServer {
	private static final Logger LOGGER = LoggerFactory.getLogger("mages_legacy-genworld");

	public void onInitializeServer() {
		if (!Boolean.getBoolean("mages_legacy.genworld")) {
			return;
		}
		NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
 var server = event.getServer();
			LOGGER.info("[genworld] world created, stopping");
			server.halt(false);
		});
	}
}
