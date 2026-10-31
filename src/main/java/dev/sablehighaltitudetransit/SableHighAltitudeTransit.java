package dev.sablehighaltitudetransit;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(SableHighAltitudeTransit.MOD_ID)
public final class SableHighAltitudeTransit {
    public static final String MOD_ID = "sable_high_altitude_transit";

    public SableHighAltitudeTransit() {
        NeoForge.EVENT_BUS.addListener(HighAltitudeTransitManager::onServerStopped);
        NeoForge.EVENT_BUS.addListener(TransitPerformanceMonitor::onServerStarted);
        NeoForge.EVENT_BUS.addListener(TransitPerformanceMonitor::onServerStopped);
    }
}
