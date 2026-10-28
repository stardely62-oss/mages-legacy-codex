package euf.mageslegacy.test;
@net.neoforged.fml.common.Mod("mages_legacy_test")
public class TestMod {
 public TestMod(net.neoforged.bus.api.IEventBus bus) {
  new GenWorldServer().onInitializeServer();
  if (Boolean.getBoolean("mages_legacy.gametest"))
   net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerAboutToStartEvent event) -> {
    try { net.minecraft.gametest.framework.GlobalTestReporter.replaceWith(new net.minecraft.gametest.framework.JUnitLikeTestReporter(new java.io.File("junit.xml"))); }
    catch (Exception e) { throw new IllegalStateException("Cannot initialize GameTest report", e); }
   });
  if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT)
   bus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) -> event.enqueueWork(() -> new HarnessClient().onInitializeClient()));
 }
}
