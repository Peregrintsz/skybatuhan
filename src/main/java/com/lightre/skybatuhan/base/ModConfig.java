package com.lightre.skybatuhan.base;

import com.google.gson.annotations.Expose;
import io.github.notenoughupdates.moulconfig.Config;
import io.github.notenoughupdates.moulconfig.Social;
import io.github.notenoughupdates.moulconfig.annotations.*;
import io.github.notenoughupdates.moulconfig.common.MyResourceLocation;
import io.github.notenoughupdates.moulconfig.common.text.StructuredText;
import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.List;

public class ModConfig extends Config {
    @Override
    public StructuredText getTitle() {
        String version = FabricLoader.getInstance()
                .getModContainer("skybatuhan")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
        return StructuredText.of("SkyBatuhan").aqua()
                .append(StructuredText.of(" v" + version + " by ").grey())
                .append(StructuredText.of("Lightre, Peregrints").red());
    }

    @Override
    public boolean isValidRunnable(int runnableId) {
        return false;
    }

    @Override
    public List<Social> getSocials() {
        List<Social> list = new ArrayList<>();
        list.add(Social.forLink(
                StructuredText.of("GitHub"),
                new MyResourceLocation("skybatuhan", "textures/github.png"),
                "https://github.com/lightre/skybatuhan"
        ));
        return list;
    }

    private static void openLink(String url) {
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
        } catch (Exception ignored) {
        }
    }

    @Expose
    @Category(name = "About", desc = "Info about SkyBatuhan")
    public AboutCategory about = new AboutCategory();

    @Expose
    @Category(name = "Farming", desc = "Auto Farm settings")
    public FarmingCategory farming = new FarmingCategory();

    @Expose
    @Category(name = "Fishing", desc = "Auto Fish settings")
    public FishingCategory fishing = new FishingCategory();

    public static class AboutCategory {
        @ConfigOption(name = "SkyBatuhan", desc = "Farming and fishing automation for Minecraft 26.2.")
        @ConfigEditorInfoText
        public transient String info = "";

        @ConfigOption(name = "Authors", desc = "Lightre, Peregrints")
        @ConfigEditorInfoText
        public transient String authors = "";

        @Expose
        @Accordion
        @ConfigOption(name = "Libraries", desc = "What SkyBatuhan is built on")
        public LibrariesCategory libraries = new LibrariesCategory();

        @ConfigOption(name = "GitHub", desc = "Open the project page")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openGithub = () -> openLink("https://github.com/lightre/skybatuhan");

        @ConfigOption(name = "Report a bug", desc = "Open the issue tracker")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openIssues = () -> openLink("https://github.com/lightre/skybatuhan/issues");
    }

    public static class LibrariesCategory {
        @ConfigOption(name = "Minecraft", desc = "26.2 (Java 25)")
        @ConfigEditorInfoText
        public transient String minecraft = "";

        @ConfigOption(name = "Fabric Loader", desc = "v0.19.5, mod loading")
        @ConfigEditorInfoText
        public transient String loader = "";

        @ConfigOption(name = "Fabric API", desc = "v0.161.0, events, keybinds and rendering hooks")
        @ConfigEditorInfoText
        public transient String fabricApi = "";

        @ConfigOption(name = "MoulConfig", desc = "v4.7.2, this settings menu")
        @ConfigEditorInfoText
        public transient String moulConfig = "";

        @ConfigOption(name = "MoulConfig on GitHub", desc = "Open the library page")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openMoulConfig = () -> openLink("https://github.com/NotEnoughUpdates/MoulConfig");

        @ConfigOption(name = "Fabric", desc = "Open the Fabric website")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openFabric = () -> openLink("https://fabricmc.net");
    }

    public static class SafetyCategory {
        @Expose
        @ConfigOption(name = "Timeout (ms)", desc = "Safety timeout")
        @ConfigEditorSlider(minValue = 0f, maxValue = 10000f, minStep = 10f)
        public int timeoutMs = 3000;

        @Expose
        @ConfigOption(name = "Threshold", desc = "Safety threshold")
        @ConfigEditorSlider(minValue = 0f, maxValue = 1f, minStep = 0.5f)
        public float threshold = 0.1f;
    }

    public static class FarmingCategory {
        @Expose
        @ConfigOption(name = "Auto Farm", desc = "Toggle Auto Farm on/off")
        @ConfigEditorBoolean
        public boolean autoFarmEnabled = false;

        @Expose
        @Accordion
        @ConfigOption(name = "General Settings", desc = "")
        public GeneralSettings general = new GeneralSettings();

        @Expose
        @Accordion
        @ConfigOption(name = "Movements", desc = "")
        public FarmingMovements farmingMovements = new FarmingMovements();

        @Expose
        @Accordion
        @ConfigOption(name = "Safety Settings", desc = "")
        public SafetyCategory safety = new SafetyCategory();
    }

    public static class GeneralSettings {
        @Expose
        @ConfigOption(name = "Point Range", desc = "Waypoint reach range")
        @ConfigEditorSlider(minValue = 0f, maxValue = 3f, minStep = 0.5f)
        public double pointRange = 0.1f;

        @Expose
        @ConfigOption(name = "Attack Enabled", desc = "Attack nearby targets while farming")
        @ConfigEditorBoolean
        public boolean attackEnabled = true;
    }

    public static class FarmingMovements {
        @Expose
        @Accordion
        @ConfigOption(name = "First Movement", desc = "")
        public MoveSettings firstMove = new MoveSettings();

        @Expose
        @Accordion
        @ConfigOption(name = "Second Movement", desc = "")
        public MoveSettings secondMove = new MoveSettings();
    }

    public static class MoveSettings {
        @Expose @ConfigOption(name = "Forward", desc = "") @ConfigEditorBoolean
        public boolean forward = false;
        @Expose @ConfigOption(name = "Back", desc = "") @ConfigEditorBoolean
        public boolean back = false;
        @Expose @ConfigOption(name = "Left", desc = "") @ConfigEditorBoolean
        public boolean left = false;
        @Expose @ConfigOption(name = "Right", desc = "") @ConfigEditorBoolean
        public boolean right = false;
    }

    public static class FishingCategory {
        @Expose
        @ConfigOption(name = "Auto Fish", desc = "Toggle Auto Fish on/off")
        @ConfigEditorBoolean
        public boolean autoFishEnabled = false;

        @Expose
        @ConfigOption(name = "Fishing Mode", desc = "Vanilla or Skyblock")
        @ConfigEditorDropdown(values = {"Vanilla", "Skyblock"})
        public String fishMode = "Vanilla";

        @Expose
        @ConfigOption(name = "Jump On Reel", desc = "Jump when reeling in the rod")
        @ConfigEditorBoolean
        public boolean reelJump = false;

        @Expose
        @ConfigOption(name = "Min Reel Delay (ms)", desc = "")
        @ConfigEditorSlider(minValue = 10f, maxValue = 2000f, minStep = 10f)
        public double minReelDelay = 400.0;

        @Expose
        @ConfigOption(name = "Max Reel Delay (ms)", desc = "")
        @ConfigEditorSlider(minValue = 10f, maxValue = 2000f, minStep = 10f)
        public double maxReelDelay = 1000.0;

        @Expose
        @ConfigOption(name = "Min Cast Delay (ms)", desc = "")
        @ConfigEditorSlider(minValue = 200f, maxValue = 2000f, minStep = 10f)
        public double minCastDelay = 200.0;

        @Expose
        @ConfigOption(name = "Max Cast Delay (ms)", desc = "")
        @ConfigEditorSlider(minValue = 200f, maxValue = 2000f, minStep = 10f)
        public double maxCastDelay = 1000.0;

        @Expose
        @ConfigOption(name = "AFK Timeout (s)", desc = "")
        @ConfigEditorSlider(minValue = 5f, maxValue = 180f, minStep = 1f)
        public double afkTimeoutSeconds = 30.0;

        public static final long MIN_CAST_DELAY_MS = 200;

        @Expose
        @ConfigOption(name = "Enable Action Slot", desc = "")
        @ConfigEditorBoolean
        public boolean useActionSlot = false;

        @Expose
        @ConfigOption(name = "Action Slot", desc = "")
        @ConfigEditorDropdown(values = {"Slot 1", "Slot 2", "Slot 3", "Slot 4", "Slot 5", "Slot 6", "Slot 7", "Slot 8", "Slot 9"})
        public String actionSlot = "Slot 3";
    }
}