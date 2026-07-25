package com.cosmicplayer.stafftracker;

import com.cosmicplayer.stafftracker.hud.CounterHud;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.util.Identifier;

/**
 * Staff Tracker. A lightweight counter for staff members. Press the bound
 * key each time a player is helped. Everything is stored locally.
 */
public final class StaffTrackerClient implements ClientModInitializer {
    public static final String MOD_ID = "stafftracker";

    @Override
    public void onInitializeClient() {
        StaffTrackerConfig.load();
        HelpData.load();
        CountKeyListener.register();
        CounterHud.register();
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
