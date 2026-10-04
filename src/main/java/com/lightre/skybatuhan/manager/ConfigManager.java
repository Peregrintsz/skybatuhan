package com.lightre.skybatuhan.manager;

import com.lightre.skybatuhan.base.ModConfig;
import io.github.notenoughupdates.moulconfig.managed.ManagedConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.nio.file.Path;

public class ConfigManager {
    // Directory: .minecraft/config/skybatuhan
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("skybatuhan");
    private static final File CONFIG_FILE = CONFIG_DIR.resolve("config.json").toFile();

    public static ModConfig config = new ModConfig();
    private static ManagedConfig<ModConfig> managed;

    public static ManagedConfig<ModConfig> getManaged() {
        if (managed == null) {
            File file = new File(
                    net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().toFile(),
                    "config.json" // senin mevcut dosya adın neyse onu yaz
            );
            managed = ManagedConfig.create(file, ModConfig.class);
        }
        return managed;
    }
    public static void load() {
        boolean existed = CONFIG_FILE.exists();

        try {
            CONFIG_DIR.toFile().mkdirs();

            managed = ManagedConfig.create(CONFIG_FILE, ModConfig.class);
            config = managed.getInstance();

            if (config == null) {
                config = new ModConfig();
            }

            if (config.farming == null) config.farming = new ModConfig.FarmingCategory();
            if (config.fishing == null) config.fishing = new ModConfig.FishingCategory();

            if (config.farming.safety == null) config.farming.safety = new ModConfig.SafetyCategory();
            if (config.farming.general == null) config.farming.general = new ModConfig.GeneralSettings();

            if (config.farming.farmingMovements == null)
                config.farming.farmingMovements = new ModConfig.FarmingMovements();

            if (config.farming.farmingMovements.firstMove == null)
                config.farming.farmingMovements.firstMove = new ModConfig.MoveSettings();

            if (config.farming.farmingMovements.secondMove == null)
                config.farming.farmingMovements.secondMove = new ModConfig.MoveSettings();

            config.fishing.minCastDelay = Math.max(config.fishing.minCastDelay, ModConfig.FishingCategory.MIN_CAST_DELAY_MS);
            config.fishing.maxCastDelay = Math.max(config.fishing.maxCastDelay, ModConfig.FishingCategory.MIN_CAST_DELAY_MS);

            if (config.disconnect == null) config.disconnect = new ModConfig.DisconnectCategory();
            if (config.disconnect == null) config.disconnect = new ModConfig.DisconnectCategory();
            if (config.disconnect.reconnect == null) config.disconnect.reconnect = new ModConfig.ReconnectCategory();

            if (!existed) save();

        } catch (Exception e) {
            System.err.println("[" + CONFIG_FILE.getName() + "] Ayarlar yuklenirken hata olustu!");
            e.printStackTrace();
            // ManagedConfig olusturulduysa onu koru, menu yine acilabilsin
            if (managed == null) {
                config = new ModConfig();
            }
        }
    }

    public static void save() {
        try {
            if (managed != null) {
                managed.saveToFile();
            }
        } catch (Exception e) {
            System.err.println("[" + CONFIG_FILE.getName() + "] Ayarlar kaydedilirken hata olustu.");
            e.printStackTrace();
        }
    }
}