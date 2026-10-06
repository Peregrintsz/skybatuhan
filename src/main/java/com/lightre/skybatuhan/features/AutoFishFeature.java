package com.lightre.skybatuhan.features;

import com.lightre.skybatuhan.base.Feature;
import com.lightre.skybatuhan.manager.ConfigManager;
import com.lightre.skybatuhan.base.ModConfig;
// import com.lightre.skybatuhan.manager.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class AutoFishFeature extends Feature {
    private static final ScheduledExecutorService threadScheduler = Executors.newScheduledThreadPool(1);

    private long lastHookTime = 0;
    private long lastSkyblockClickTime = 0;
    private int jumpTicksLeft = 0;
    private static final long JUMP_LEAD_MS = 100L;
    private final AtomicInteger generation = new AtomicInteger();

    private static final long ACTION_SLOT_MIN_DELAY_MS = 25L;
    private static final long ACTION_SLOT_MAX_DELAY_MS = 75L;
    private static final long CLICK_MIN_DELAY_MS = 700L;
    private static final long CLICK_MAX_DELAY_MS = 1000L;
    private static final int MAX_CLICKS = 50;
    private static final long ENTITY_RECOVERY_MIN_MS = 25L;
    private static final long ENTITY_RECOVERY_MAX_MS = 75L;
    private static final long ENTITY_RECOVERY_TIMEOUT_MS = 5000L;
    private static final double SKYBLOCK_MARKER_RADIUS_SQUARED = 9.0; // 3 blocks around the bobber
    private long lastEntityHookTime = 0L;
    private volatile boolean entityRecoveryPending = false;
    private int pendingRestoreSlot = -1;

    public AutoFishFeature() {
        super("Auto Fish");
    }

    public void onFishHooked(Minecraft client) {
        if (!this.isEnabled() || client.player == null || client.gameMode == null) return;

        final int gen = generation.get();

        System.out.println("[Client-AntiCheat-Safe] Fish hooked for local player!");

        lastHookTime = System.currentTimeMillis();

        ModConfig.FishingCategory fishConfig = ConfigManager.config.fishing;

        long minReel = (long) fishConfig.minReelDelay;
        long maxReel = (long) fishConfig.maxReelDelay;
        long minCast = (long) fishConfig.minCastDelay;
        long maxCast = (long) fishConfig.maxCastDelay;

        long actualMinReel = Math.min(minReel, maxReel);
        long actualMaxReel = Math.max(minReel, maxReel);
        long lowCast = Math.min(minCast, maxCast);
        long highCast = Math.max(minCast, maxCast);
        long actualMinCast = Math.max(ModConfig.FishingCategory.MIN_CAST_DELAY_MS, lowCast);
        long actualMaxCast = Math.max(ModConfig.FishingCategory.MIN_CAST_DELAY_MS, highCast);

        long reelDelay = ThreadLocalRandom.current().nextLong(actualMinReel, actualMaxReel + 1);
        System.out.println("[AutoFish] Reel-in scheduled with menu delay: " + reelDelay + "ms");

        long jumpOffset = Math.min(reelDelay, JUMP_LEAD_MS);
        long jumpDelay = reelDelay - jumpOffset;

        threadScheduler.schedule(() -> {
            if (client.player != null && client.gameMode != null) {
                client.execute(() -> {
                    if (!this.isEnabled() || gen != generation.get()) return;
                    if (ConfigManager.config.fishing.reelJump) {
                        startJump(client);
                        System.out.println("[AutoFish] Pre-reel jump executed right before pulling!");
                    }
                });
            }
        }, jumpDelay, TimeUnit.MILLISECONDS);

        threadScheduler.schedule(() -> {
            if (client.player != null && client.gameMode != null) {
                client.execute(() -> {
                    if (!this.isEnabled() || gen != generation.get()) return;

                    var player = client.player;
                    var gameMode = client.gameMode;
                    if (player == null || gameMode == null) return;

                    InteractionHand fishingHand = InteractionHand.MAIN_HAND;
                    if (player.getOffhandItem().is(Items.FISHING_ROD)) {
                        fishingHand = InteractionHand.OFF_HAND;
                    }

                    gameMode.useItem(player, fishingHand);
                    player.swing(fishingHand);
                    System.out.println("[AutoFish] Organic Reel-in action executed successfully.");

                    final InteractionHand finalHand = fishingHand;

                    if (shouldUseActionSlot()) {
                        scheduleActionSlot(client, finalHand, gen, actualMinCast, actualMaxCast);
                    } else {
                        continueAfterReel(client, finalHand, gen, actualMinCast, actualMaxCast);
                    }
                });
            }
        }, reelDelay, TimeUnit.MILLISECONDS);
    }

    private void scheduleRecast(Minecraft client, InteractionHand finalHand, int gen, long actualMinCast, long actualMaxCast) {
        long castDelay = ThreadLocalRandom.current().nextLong(actualMinCast, actualMaxCast + 1);
        System.out.println("[AutoFish] Recast scheduled with delay: " + castDelay + "ms");

        threadScheduler.schedule(() -> {
            if (client.player != null && client.gameMode != null) {
                client.execute(() -> {
                    if (!this.isEnabled() || gen != generation.get()) return;

                    entityRecoveryPending = false;

                    var p2 = client.player;
                    var gm2 = client.gameMode;
                    if (p2 == null || gm2 == null) return;

                    gm2.useItem(p2, finalHand);
                    p2.swing(finalHand);
                    System.out.println("[AutoFish] Organic Recast action executed successfully. Loop continues!");

                    lastSkyblockClickTime = System.currentTimeMillis();
                });
            }
        }, castDelay, TimeUnit.MILLISECONDS);
    }

    // ================= ACTION SLOT =================
    private boolean shouldUseActionSlot() {
        return ConfigManager.config.fishing.useActionSlot && ConfigManager.config.fishing.actionSlot != null;
    }

    private int parseActionSlot() {
        return parseSlot(ConfigManager.config.fishing.actionSlot);
    }

    private int parseSlot(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        int slot = Integer.parseInt(digits) - 1;
        return Math.max(0, Math.min(8, slot));
    }

    private void scheduleActionSlot(Minecraft client, InteractionHand finalHand, int gen, long actualMinCast, long actualMaxCast) {
        int targetSlot = parseActionSlot();
        long switchDelay = ThreadLocalRandom.current().nextLong(ACTION_SLOT_MIN_DELAY_MS, ACTION_SLOT_MAX_DELAY_MS + 1);

        threadScheduler.schedule(() -> {
            if (client.player != null && client.gameMode != null) {
                client.execute(() -> executeActionSlot(client, finalHand, gen, targetSlot, actualMinCast, actualMaxCast));
            }
        }, switchDelay, TimeUnit.MILLISECONDS);
    }

    private void executeActionSlot(Minecraft client, InteractionHand finalHand, int gen, int targetSlot, long actualMinCast, long actualMaxCast) {
        if (!this.isEnabled() || gen != generation.get()) return;

        var player = client.player;
        var gameMode = client.gameMode;
        if (player == null || gameMode == null) return;

        int originalSlot = player.getInventory().getSelectedSlot();
        pendingRestoreSlot = originalSlot;
        player.getInventory().setSelectedSlot(targetSlot);

        gameMode.useItem(player, InteractionHand.MAIN_HAND);
        player.swing(InteractionHand.MAIN_HAND);
        System.out.println("[AutoFish] Action slot activated");

        long returnDelay = ThreadLocalRandom.current().nextLong(ACTION_SLOT_MIN_DELAY_MS, ACTION_SLOT_MAX_DELAY_MS + 1);

        threadScheduler.schedule(() -> {
            if (client.player != null) {
                client.execute(() -> returnToRodSlot(client, finalHand, gen, originalSlot, actualMinCast, actualMaxCast));
            }
        }, returnDelay, TimeUnit.MILLISECONDS);
    }

    private void returnToRodSlot(Minecraft client, InteractionHand finalHand, int gen, int originalSlot, long actualMinCast, long actualMaxCast) {
        var player = client.player;
        if (player == null) return;

        player.getInventory().setSelectedSlot(originalSlot);
        pendingRestoreSlot = -1;

        if (!this.isEnabled() || gen != generation.get()) return;

        System.out.println("[AutoFish] Returned to rod slot");
        continueAfterReel(client, finalHand, gen, actualMinCast, actualMaxCast);
    }

    // ================= CLICK SLOT =================
    // After reeling (and the action slot, if enabled): switch to the click slot,
    // left click N times (arm swing), switch back and recast.
    private void continueAfterReel(Minecraft client, InteractionHand finalHand, int gen, long actualMinCast, long actualMaxCast) {
        if (shouldUseClickSlot()) {
            scheduleClickSlot(client, finalHand, gen, actualMinCast, actualMaxCast);
        } else {
            scheduleRecast(client, finalHand, gen, actualMinCast, actualMaxCast);
        }
    }

    private boolean shouldUseClickSlot() {
        return ConfigManager.config.fishing.useClickSlot && ConfigManager.config.fishing.clickSlot != null;
    }

    private int clickCount() {
        int clicks = (int) Math.round(ConfigManager.config.fishing.clickCount);
        return Math.max(1, Math.min(MAX_CLICKS, clicks));
    }

    private void scheduleClickSlot(Minecraft client, InteractionHand finalHand, int gen, long actualMinCast, long actualMaxCast) {
        int targetSlot = parseSlot(ConfigManager.config.fishing.clickSlot);
        int clicks = clickCount();
        long switchDelay = ThreadLocalRandom.current().nextLong(ACTION_SLOT_MIN_DELAY_MS, ACTION_SLOT_MAX_DELAY_MS + 1);

        threadScheduler.schedule(() -> client.execute(() -> {
            if (!this.isEnabled() || gen != generation.get()) return;

            var player = client.player;
            if (player == null) return;

            int originalSlot = player.getInventory().getSelectedSlot();
            pendingRestoreSlot = originalSlot;
            player.getInventory().setSelectedSlot(targetSlot);
            System.out.println("[AutoFish] Click slot activated, clicks: " + clicks);

            clickStep(client, finalHand, gen, originalSlot, clicks, actualMinCast, actualMaxCast);
        }), switchDelay, TimeUnit.MILLISECONDS);
    }

    // Same as a vanilla left click: hit the entity under the crosshair (if any), then swing
    private void leftClick(Minecraft client) {
        var player = client.player;
        var gameMode = client.gameMode;
        if (player == null) return;

        if (gameMode != null && client.hitResult instanceof EntityHitResult entityHit) {
            gameMode.attack(player, entityHit.getEntity());
        }
        player.swing(InteractionHand.MAIN_HAND);
    }

    private void clickStep(Minecraft client, InteractionHand finalHand, int gen, int originalSlot, int clicksLeft, long actualMinCast, long actualMaxCast) {
        long delay = ThreadLocalRandom.current().nextLong(CLICK_MIN_DELAY_MS, CLICK_MAX_DELAY_MS + 1);

        threadScheduler.schedule(() -> client.execute(() -> {
            if (!this.isEnabled() || gen != generation.get()) return;

            var player = client.player;
            if (player == null) return;

            if (clicksLeft > 0) {
                leftClick(client);
                clickStep(client, finalHand, gen, originalSlot, clicksLeft - 1, actualMinCast, actualMaxCast);
            } else {
                player.getInventory().setSelectedSlot(originalSlot);
                pendingRestoreSlot = -1;
                System.out.println("[AutoFish] Click slot done, back to rod slot");
                scheduleRecast(client, finalHand, gen, actualMinCast, actualMaxCast);
            }
        }), delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null || !this.isEnabled()) return;

        tickJump(client);
        handleEntityHookRecovery(client);

        String currentMode = ConfigManager.config.fishing.fishMode;

        // ================= SKYBLOCK MODE =================
        // The rod must be in the water, and the "!!!" marker must be right next to the bobber
        if ("Skyblock".equalsIgnoreCase(currentMode) && client.player.fishing != null) {
            long now = System.currentTimeMillis();
            if (now - lastSkyblockClickTime > 3000) {
                Entity bobber = client.player.fishing;
                for (Entity entity : client.level.entitiesForRendering()) {
                    if (entity instanceof ArmorStand || entity.getType().toString().contains("armor_stand")) {
                        if (entity.hasCustomName() && entity.getCustomName() != null) {
                            String nameString = entity.getCustomName().getString();
                            if (nameString.contains("!!!") && entity.distanceToSqr(bobber) <= SKYBLOCK_MARKER_RADIUS_SQUARED) {
                                System.out.println("[AutoFish] Skyblock ArmorStand '!!!' detected near bobber! Triggering organic loop...");
                                lastSkyblockClickTime = now;
                                onFishHooked(client);
                                break;
                            }
                        }
                    }
                }
            }
        }

        // ================= AFK / LAG TIME OUT PROTECTION =================
        long timeoutMs = (long) (ConfigManager.config.fishing.afkTimeoutSeconds * 1000);

        if (lastHookTime > 0 && (System.currentTimeMillis() - lastHookTime > timeoutMs)) {
            client.player.sendSystemMessage(Component.literal("§c§l[WARNING] §fSystem stopped! AFK/Lag safety timeout triggered."));

            playSafetyAlarm(client);
            this.toggle(client);
        }
    }

    @Override
    public void onToggle(Minecraft client, boolean state) {
        int gen = generation.incrementAndGet();
        entityRecoveryPending = false;
        if (state) {
            lastHookTime = System.currentTimeMillis();
            lastSkyblockClickTime = 0;
            lastEntityHookTime = 0L;
            scheduleInitialCast(client, gen);
        }
        releaseJump(client);

        if (pendingRestoreSlot >= 0 && client.player != null) {
            client.player.getInventory().setSelectedSlot(pendingRestoreSlot);
        }
        pendingRestoreSlot = -1;
    }

    // ================= ENTITY HOOK RECOVERY =================
    private boolean isEntityHooked(Minecraft client) {
        return client.player != null && client.player.fishing != null && client.player.fishing.getHookedIn() != null;
    }

    private void handleEntityHookRecovery(Minecraft client) {
        if (!isEntityHooked(client)) return;

        long now = System.currentTimeMillis();
        // One recovery at a time: it stays pending until the recast is done (5s safety limit)
        if (entityRecoveryPending && now - lastEntityHookTime < ENTITY_RECOVERY_TIMEOUT_MS) return;
        entityRecoveryPending = true;
        lastEntityHookTime = now;

        final int gen = generation.get();
        long recoveryDelay = ThreadLocalRandom.current().nextLong(ENTITY_RECOVERY_MIN_MS, ENTITY_RECOVERY_MAX_MS + 1);
        System.out.println("[AutoFish] Entity hook detected, recovery in " + recoveryDelay + "ms");

        threadScheduler.schedule(() -> client.execute(() -> recoverFromEntityHook(client, gen)), recoveryDelay, TimeUnit.MILLISECONDS);
    }

    private void recoverFromEntityHook(Minecraft client, int gen) {
        if (!this.isEnabled() || gen != generation.get()) {
            entityRecoveryPending = false;
            return;
        }

        var player = client.player;
        var gameMode = client.gameMode;
        if (player == null || gameMode == null) {
            entityRecoveryPending = false;
            return;
        }

        InteractionHand hand = player.getMainHandItem().is(Items.FISHING_ROD) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;

        // Only reel in if the bobber is still out, otherwise this click would cast it again
        if (player.fishing != null) {
            gameMode.useItem(player, hand);
            player.swing(hand);
            System.out.println("[AutoFish] Recovered from entity hook");
        }

        ModConfig.FishingCategory fishConfig = ConfigManager.config.fishing;
        long lowCast = Math.min((long) fishConfig.minCastDelay, (long) fishConfig.maxCastDelay);
        long highCast = Math.max((long) fishConfig.minCastDelay, (long) fishConfig.maxCastDelay);
        long minCast = Math.max(ModConfig.FishingCategory.MIN_CAST_DELAY_MS, lowCast);
        long maxCast = Math.max(ModConfig.FishingCategory.MIN_CAST_DELAY_MS, highCast);

        scheduleRecast(client, hand, gen, minCast, maxCast);
    }

    // ================= INITIAL CAST =================
    private void scheduleInitialCast(Minecraft client, int gen) {
        if (client.player == null) return;

        InteractionHand rodHand = null;
        if (client.player.getMainHandItem().is(Items.FISHING_ROD)) {
            rodHand = InteractionHand.MAIN_HAND;
        } else if (client.player.getOffhandItem().is(Items.FISHING_ROD)) {
            rodHand = InteractionHand.OFF_HAND;
        }

        if (rodHand == null) {
            client.player.sendSystemMessage(Component.literal("§c[AutoFish] §fHold a fishing rod to start."));
            return;
        }

        // Bobber is already in the water: casting again would reel it in
        if (client.player.fishing != null) return;

        final InteractionHand castHand = rodHand;
        long delay = ThreadLocalRandom.current().nextLong(150L, 400L);

        threadScheduler.schedule(() -> client.execute(() -> {
            if (!this.isEnabled() || gen != generation.get()) return;

            var player = client.player;
            var gameMode = client.gameMode;
            if (player == null || gameMode == null) return;
            if (player.fishing != null) return;

            gameMode.useItem(player, castHand);
            player.swing(castHand);
            System.out.println("[AutoFish] Initial cast executed");
        }), delay, TimeUnit.MILLISECONDS);
    }

    private void startJump(Minecraft client) {
        client.options.keyJump.setDown(true);
        jumpTicksLeft = 2;
    }

    private void tickJump(Minecraft client) {
        if (jumpTicksLeft > 0 && --jumpTicksLeft == 0) {
            client.options.keyJump.setDown(false);
        }
    }

    private void releaseJump(Minecraft client) {
        if (jumpTicksLeft > 0) {
            client.options.keyJump.setDown(false);
        }
        jumpTicksLeft = 0;
    }

    private void playSafetyAlarm(Minecraft client) {
        new Thread(() -> {
            for (int i = 0; i < 15; i++) {
                if (client.player != null) {
                    float pitch = 1.0f + ((i % 3) * 0.2f);
                    client.execute(() -> {
                        if (client.player != null) {
                            client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 2.0f, pitch);
                        }
                    });
                }
                try {
                    Thread.sleep(80);
                } catch (Exception ignored) {
                }
            }
        }).start();
    }
}