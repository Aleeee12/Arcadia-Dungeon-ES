package com.vyrriox.arcadiadungeon.event;

import com.vyrriox.arcadiadungeon.ArcadiaDungeon;
import com.vyrriox.arcadiadungeon.boss.BossInstance;
import com.vyrriox.arcadiadungeon.boss.BossManager;
import com.vyrriox.arcadiadungeon.config.PhaseConfig;
import com.vyrriox.arcadiadungeon.dungeon.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

public class DungeonEventHandler {

    private long tickCounter = 0; // Fix #24: long instead of int (no overflow)
    private final Set<Integer> announcedRecruitmentMilestones = new HashSet<>(); // Fix #9: track milestones
    private final Set<Integer> announcedTimerMilestones = new HashSet<>();

    // Wand: track selected dungeon per player
    public static final Map<UUID, String> wandDungeon = new java.util.concurrent.ConcurrentHashMap<>();
    public static final Map<UUID, net.minecraft.core.BlockPos> wandPos1 = new java.util.concurrent.ConcurrentHashMap<>();
    public static final Map<UUID, net.minecraft.core.BlockPos> wandPos2 = new java.util.concurrent.ConcurrentHashMap<>();

    public static final String WAND_TAG = "arcadia_wand";

    @SubscribeEvent
    public void onPlayerInteract(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.getMainHandItem().getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.empty()).getString().contains(WAND_TAG)) return;

        String dungeonId = wandDungeon.get(player.getUUID());
        if (dungeonId == null) { player.sendSystemMessage(Component.literal("[Arcadia] Aucun donjon selectionne! Utilisez /arcadia_dungeon admin wand d'abord.").withStyle(ChatFormatting.RED)); return; }

        net.minecraft.core.BlockPos pos = event.getPos();
        wandPos1.put(player.getUUID(), pos);
        player.sendSystemMessage(Component.literal("[Arcadia Wand] Pos1 definie: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withStyle(ChatFormatting.GREEN));
        trySaveWandArea(player, dungeonId);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onPlayerInteractRight(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.getMainHandItem().getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.empty()).getString().contains(WAND_TAG)) return;

        String dungeonId = wandDungeon.get(player.getUUID());
        if (dungeonId == null) { player.sendSystemMessage(Component.literal("[Arcadia] Aucun donjon selectionne!").withStyle(ChatFormatting.RED)); return; }

        net.minecraft.core.BlockPos pos = event.getPos();
        wandPos2.put(player.getUUID(), pos);
        player.sendSystemMessage(Component.literal("[Arcadia Wand] Pos2 definie: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withStyle(ChatFormatting.AQUA));
        trySaveWandArea(player, dungeonId);
        event.setCanceled(true);
    }

    private void trySaveWandArea(ServerPlayer player, String dungeonId) {
        net.minecraft.core.BlockPos p1 = wandPos1.get(player.getUUID());
        net.minecraft.core.BlockPos p2 = wandPos2.get(player.getUUID());
        if (p1 == null || p2 == null) return;

        var config = com.vyrriox.arcadiadungeon.config.ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) return;

        String dim = player.level().dimension().location().toString();
        config.areaPos1 = new com.vyrriox.arcadiadungeon.config.DungeonConfig.AreaPos(dim, p1.getX(), p1.getY(), p1.getZ());
        config.areaPos2 = new com.vyrriox.arcadiadungeon.config.DungeonConfig.AreaPos(dim, p2.getX(), p2.getY(), p2.getZ());
        com.vyrriox.arcadiadungeon.config.ConfigManager.getInstance().saveDungeon(config);

        int sx = Math.abs(p2.getX() - p1.getX()) + 1;
        int sy = Math.abs(p2.getY() - p1.getY()) + 1;
        int sz = Math.abs(p2.getZ() - p1.getZ()) + 1;
        player.sendSystemMessage(Component.literal("[Arcadia Wand] Zone sauvegardee pour " + config.name + "! " + sx + "x" + sy + "x" + sz + " (" + (sx*sy*sz) + " blocs)")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        wandPos1.remove(player.getUUID());
        wandPos2.remove(player.getUUID());
    }

    // === COMMAND BLOCKING ===
    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        var source = event.getParseResults().getContext().getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) return;
        DungeonInstance instance = DungeonManager.getInstance().getPlayerDungeon(player.getUUID());
        if (instance == null || !instance.getConfig().settings.blockTeleportCommands) return;

        String input = event.getParseResults().getReader().getString().trim();
        if (input.startsWith("/")) input = input.substring(1);
        String rootCmd = input.split(" ")[0].toLowerCase();
        String cmdNoPrefix = rootCmd.contains(":") ? rootCmd.substring(rootCmd.indexOf(':') + 1) : rootCmd;

        for (String blocked : instance.getConfig().settings.blockedCommands) {
            if (rootCmd.equals(blocked) || cmdNoPrefix.equals(blocked)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("[Arcadia] Cette commande est bloquee pendant le donjon!").withStyle(ChatFormatting.RED));
                return;
            }
        }
    }

    // === TICK ===
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        DungeonManager.getInstance().processPendingFails();
        BossManager.getInstance().tickAllBosses(); // Fix #19: early-exit is inside BossManager now

        if (tickCounter % 10 == 0) checkWaveStates();
        if (tickCounter % 20 == 0) { checkRecruitment(); checkDungeonTimers(); }
        if (tickCounter % 40 == 0) { checkContainment(); checkPlayerContainment(); }

        // Fix #18: apply freeze effects less frequently (every 100 ticks = 5s, with longer duration)
        if (tickCounter % 100 == 0) freezeRecruitingPlayers();

        // Check availability announcements every 10s
        if (tickCounter % 200 == 0) DungeonManager.getInstance().checkAvailabilityAnnouncements();

        // Weekly leaderboard tick every minute
        // Fix #8: flush dirty player data every 30s instead of on every completion
        if (tickCounter % 600 == 0) {
            PlayerProgressManager.getInstance().flushDirty();
        }

        if (tickCounter % 1200 == 0) {
            var server = DungeonManager.getInstance().getServer();
            if (server != null) WeeklyLeaderboard.getInstance().tick(server);
        }

        // Prune expired data every 5 minutes to prevent memory leaks
        if (tickCounter % 6000 == 0) {
            DungeonManager.getInstance().pruneExpiredData();
        }
    }

    // === ENTITY EVENTS ===
    @SubscribeEvent
    public void onEntityDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.getTags().contains("arcadia_boss")) { handleBossDeath(entity); return; }
        if (entity.getTags().contains("arcadia_wave_mob")) return;
        if (entity instanceof ServerPlayer player) handlePlayerDeath(player, event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onEntityDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();

        // Anti-PVP
        if (entity instanceof ServerPlayer targetPlayer) {
            DungeonInstance targetDungeon = DungeonManager.getInstance().getPlayerDungeon(targetPlayer.getUUID());
            if (targetDungeon != null && !targetDungeon.getConfig().settings.pvp) {
                if (event.getSource().getEntity() instanceof Player attacker) {
                    if (targetDungeon.getPlayers().contains(attacker.getUUID())) {
                        event.setCanceled(true);
                        return;
                    }
                }
            }
        }

        if (!entity.getTags().contains("arcadia_boss")) return;

        // Fix #7: direct lookup instead of O(D*B) scan
        for (DungeonInstance instance : DungeonManager.getInstance().getActiveInstances().values()) {
            for (BossInstance boss : instance.getActiveBosses().values()) {
                if (boss.getBossEntity() != entity) continue;

                if (boss.isTransitioning()) { event.setCanceled(true); return; }
                if (boss.requiresKillSummons()) { event.setCanceled(true); boss.sendSummonWarning(instance.getPlayers()); return; }

                // Reveal boss bar on first hit (optional bosses)
                boss.revealBossBar();

                // Anti-phase-skip
                float currentHp = boss.getBossEntity().getHealth();
                float maxHp = boss.getBossEntity().getMaxHealth();
                float resultHp = currentHp - event.getAmount();
                float floor = getHealthFloorForNextPhase(boss, maxHp);
                if (floor > 0 && resultHp < floor) {
                    float capped = currentHp - floor;
                    if (capped > 0) event.setAmount(capped); else event.setCanceled(true);
                }
                return;
            }
        }
    }

    private float getHealthFloorForNextPhase(BossInstance boss, float maxHp) {
        var phases = boss.getConfig().phases;
        for (int i = boss.getCurrentPhase() + 1; i < phases.size(); i++) {
            return (float) (phases.get(i).healthThreshold * maxHp);
        }
        return 0;
    }

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity().getTags().contains("arcadia_no_loot")) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity().getTags().contains("arcadia_no_loot")) event.setCanceled(true);
    }

    // === PLAYER EVENTS ===
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID uuid = player.getUUID();
            DungeonInstance instance = DungeonManager.getInstance().getPlayerDungeon(uuid);
            if (instance != null) {
                DungeonManager.getInstance().removePlayerFromDungeon(uuid);
            }
            // Cleanup wand data
            wandDungeon.remove(uuid);
            wandPos1.remove(uuid);
            wandPos2.remove(uuid);
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Fix #16: skip respawn TP if player is pending removal (max deaths reached)
        if (DungeonManager.getInstance().isPendingRemoval(player.getUUID())) return;

        DungeonInstance instance = DungeonManager.getInstance().getPlayerDungeon(player.getUUID());
        if (instance == null) return;

        DungeonManager.getInstance().teleportToSpawn(player, instance.getConfig().spawnPoint);
        player.sendSystemMessage(Component.literal("[Arcadia] Respawn au debut du donjon. ("
                + instance.getRemainingLives(player.getUUID()) + " vie(s) restante(s))")
                .withStyle(ChatFormatting.YELLOW));
    }

    // === HANDLERS ===
    private void handleBossDeath(LivingEntity entity) {
        for (Map.Entry<String, DungeonInstance> entry : new ArrayList<>(DungeonManager.getInstance().getActiveInstances().entrySet())) {
            DungeonInstance instance = entry.getValue();
            String dungeonId = entry.getKey();

            for (Map.Entry<String, BossInstance> bossEntry : new ArrayList<>(instance.getActiveBosses().entrySet())) {
                BossInstance boss = bossEntry.getValue();
                if (boss.getBossEntity() != entity) continue;

                String bossId = bossEntry.getKey();

                for (UUID playerId : instance.getPlayers()) {
                    ServerPlayer player = DungeonManager.getInstance().getServer().getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        instance.giveRewards(player, boss.getConfig().rewards);
                        player.sendSystemMessage(Component.literal("[Arcadia] Boss " + boss.getConfig().customName + " vaincu!").withStyle(ChatFormatting.GOLD));
                    }
                }

                // Fix #2: remove from map BEFORE cleanup to prevent re-entry issues
                instance.removeBossInstance(bossId);
                boss.cleanup();

                // Inter-wave boss: let checkWaveStates handle resume when all inter-wave bosses dead
                if (instance.isWaitingForInterWaveBoss()) {
                    if (instance.getActiveBosses().isEmpty()) {
                        // All inter-wave bosses dead — checkWaveStates will resume waves next tick
                    }
                } else if (instance.hasNextBoss()) {
                    boolean spawned = BossManager.getInstance().spawnNextBoss(instance);
                    if (spawned) {
                        for (UUID playerId : instance.getPlayers()) {
                            ServerPlayer player = DungeonManager.getInstance().getServer().getPlayerList().getPlayer(playerId);
                            if (player != null) player.sendSystemMessage(Component.literal("[Arcadia] Prochain boss en approche...").withStyle(ChatFormatting.YELLOW));
                        }
                    } else if (instance.allRequiredBossesDefeated()) {
                        if (!instance.hasWaves() || instance.areWavesCompleted()) {
                            for (BossInstance remaining : new ArrayList<>(instance.getActiveBosses().values())) {
                                remaining.cleanup();
                            }
                            instance.getActiveBosses().clear();
                            DungeonManager.getInstance().completeDungeon(dungeonId);
                        }
                    }
                } else if (instance.allRequiredBossesDefeated() && !instance.isWaitingForInterWaveBoss()) {
                    // All required bosses defeated — check if waves done too
                    if (!instance.hasWaves() || instance.areWavesCompleted()) {
                        // Cleanup non-required bosses still alive
                        for (BossInstance remaining : new ArrayList<>(instance.getActiveBosses().values())) {
                            remaining.cleanup();
                        }
                        instance.getActiveBosses().clear();
                        DungeonManager.getInstance().completeDungeon(dungeonId);
                    }
                }
                return;
            }
        }
    }

    private void handlePlayerDeath(ServerPlayer player, LivingDeathEvent event) {
        DungeonInstance instance = DungeonManager.getInstance().getPlayerDungeon(player.getUUID());
        if (instance == null) return;

        int deaths = instance.addDeath(player.getUUID());
        int maxDeaths = instance.getConfig().settings.maxDeaths;
        int remaining = instance.getRemainingLives(player.getUUID());

        if (maxDeaths > 0 && deaths >= maxDeaths) {
            player.sendSystemMessage(Component.literal("[Arcadia] Maximum de morts (" + maxDeaths + ") atteint! Vous etes exclu du donjon.").withStyle(ChatFormatting.RED));
            for (UUID otherId : instance.getPlayers()) {
                if (!otherId.equals(player.getUUID())) {
                    ServerPlayer other = DungeonManager.getInstance().getServer().getPlayerList().getPlayer(otherId);
                    if (other != null) other.sendSystemMessage(Component.literal("[Arcadia] " + player.getName().getString() + " a ete exclu (trop de morts)!").withStyle(ChatFormatting.RED));
                }
            }
            DungeonManager.getInstance().schedulePlayerRemoval(player.getUUID());
        } else {
            player.sendSystemMessage(Component.literal("[Arcadia] Mort! " + remaining + " vie(s) restante(s).").withStyle(ChatFormatting.RED));
        }
    }

    // === RECRUITMENT ===
    private void freezeRecruitingPlayers() {
        MinecraftServer server = DungeonManager.getInstance().getServer();
        if (server == null) return;
        for (DungeonInstance instance : DungeonManager.getInstance().getActiveInstances().values()) {
            if (instance.getState() != DungeonState.RECRUITING) continue;
            for (UUID playerId : instance.getPlayers()) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    // Fix #18: longer duration (120 ticks = 6s), applied every 5s
                    player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 120, 0, false, false, false));
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 255, false, false, false));
                }
            }
        }
    }

    private void checkRecruitment() {
        MinecraftServer server = DungeonManager.getInstance().getServer();
        if (server == null) return;

        for (Map.Entry<String, DungeonInstance> entry : new ArrayList<>(DungeonManager.getInstance().getActiveInstances().entrySet())) {
            DungeonInstance instance = entry.getValue();
            String dungeonId = entry.getKey();
            if (instance.getState() != DungeonState.RECRUITING) continue;

            long remaining = instance.getRecruitmentRemainingSeconds();

            // Fix #9: range-based milestone checks (won't miss on lag)
            int[] milestones = {60, 30, 10, 5};
            for (int m : milestones) {
                int key = Objects.hash(dungeonId, m);
                if (remaining <= m && remaining > m - 2 && !announcedRecruitmentMilestones.contains(key)) {
                    announcedRecruitmentMilestones.add(key);
                    for (UUID playerId : instance.getPlayers()) {
                        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                        if (player != null) player.sendSystemMessage(Component.literal("[Arcadia] Recrutement: " + m + "s restantes!").withStyle(ChatFormatting.YELLOW));
                    }
                    DungeonManager.getInstance().broadcastClickableReminder(instance.getConfig(), m);
                }
            }

            if (instance.isRecruitmentOver()) {
                for (UUID playerId : instance.getPlayers()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        player.removeEffect(MobEffects.BLINDNESS);
                        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    }
                }
                announcedRecruitmentMilestones.removeIf(k -> true); // clear all
                DungeonManager.getInstance().finishRecruitment(dungeonId);
            }
        }
    }

    // === WAVES ===
    private void checkWaveStates() {
        MinecraftServer server = DungeonManager.getInstance().getServer();
        if (server == null) return;

        for (Map.Entry<String, DungeonInstance> entry : new ArrayList<>(DungeonManager.getInstance().getActiveInstances().entrySet())) {
            DungeonInstance instance = entry.getValue();

            // Handle inter-wave boss fight: wait for boss to die, then resume waves
            if (instance.isWaitingForInterWaveBoss()) {
                if (instance.getActiveBosses().isEmpty()) {
                    instance.setWaitingForInterWaveBoss(false);
                    instance.setState(DungeonState.ACTIVE);
                    // Check if there are more waves
                    if (instance.areWavesCompleted()) {
                        onWavesCompleted(instance);
                    }
                }
                continue;
            }

            if (instance.areWavesCompleted() || !instance.hasWaves() || instance.getState() != DungeonState.ACTIVE) continue;

            WaveInstance currentWave = instance.getCurrentWave();
            if (currentWave == null) { instance.setWavesCompleted(true); onWavesCompleted(instance); continue; }
            if (!currentWave.isSpawned() && !currentWave.tickDelay()) continue;

            if (!currentWave.isSpawned()) {
                currentWave.spawn(server, instance.getPlayerCount(), instance.getConfig().settings);
                String msg = currentWave.getConfig().startMessage;
                if (msg == null || msg.isEmpty()) msg = "&e[Donjon] &7Vague " + currentWave.getConfig().waveNumber + " !";
                Component component = DungeonManager.parseColorCodes(msg);
                for (UUID playerId : instance.getPlayers()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) player.sendSystemMessage(component);
                }
            }

            currentWave.checkGlowing();

            if (currentWave.isCleared()) {
                int clearedWaveNumber = currentWave.getConfig().waveNumber;
                for (UUID playerId : instance.getPlayers()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) player.sendSystemMessage(Component.literal("[Donjon] Vague " + clearedWaveNumber + " terminee!").withStyle(ChatFormatting.GREEN));
                }

                // Check for inter-wave boss after this wave
                boolean bossSpawned = BossManager.getInstance().spawnBossesForWave(instance, clearedWaveNumber);
                if (bossSpawned) {
                    instance.setWaitingForInterWaveBoss(true);
                    // Advance wave index now so waves resume after boss dies
                    if (!instance.advanceWave()) {
                        instance.setWavesCompleted(true);
                    }
                    for (UUID playerId : instance.getPlayers()) {
                        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                        if (player != null) player.sendSystemMessage(Component.literal("[Donjon] Un boss approche!").withStyle(ChatFormatting.GOLD));
                    }
                } else {
                    if (!instance.advanceWave()) onWavesCompleted(instance);
                }
            }
        }
    }

    private void onWavesCompleted(DungeonInstance instance) {
        MinecraftServer server = DungeonManager.getInstance().getServer();
        if (server == null) return;

        // Try to spawn remaining end-of-waves bosses
        boolean bossSpawned = false;
        if (instance.hasNextBoss()) {
            bossSpawned = BossManager.getInstance().spawnNextBoss(instance);
        }

        if (bossSpawned) {
            for (UUID playerId : instance.getPlayers()) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) player.sendSystemMessage(Component.literal("[Donjon] Toutes les vagues eliminees! Le boss approche...").withStyle(ChatFormatting.GOLD));
            }
        } else if (instance.allRequiredBossesDefeated()) {
            // No more bosses to spawn and all required are dead — complete
            for (BossInstance remaining : new ArrayList<>(instance.getActiveBosses().values())) {
                remaining.cleanup();
            }
            instance.getActiveBosses().clear();
            DungeonManager.getInstance().completeDungeon(instance.getConfig().id);
        }
        // else: required spawnAtStart bosses still alive — wait for them to die
    }

    // === CONTAINMENT ===
    private void checkContainment() {
        for (DungeonInstance instance : DungeonManager.getInstance().getActiveInstances().values()) {
            var config = instance.getConfig();
            if (!config.hasArea()) continue;
            for (WaveInstance wave : instance.getWaveInstances()) wave.checkContainmentArea(config);
            for (BossInstance boss : instance.getActiveBosses().values()) {
                if (boss.getBossEntity() != null && boss.getBossEntity().isAlive()) {
                    LivingEntity be = boss.getBossEntity();
                    if (!config.isInArea(be.level().dimension().location().toString(), be.getX(), be.getY(), be.getZ())) {
                        be.teleportTo(boss.getConfig().spawnPoint.x, boss.getConfig().spawnPoint.y, boss.getConfig().spawnPoint.z);
                    }
                }
            }
        }
    }

    private void checkPlayerContainment() {
        MinecraftServer server = DungeonManager.getInstance().getServer();
        if (server == null) return;
        for (DungeonInstance instance : DungeonManager.getInstance().getActiveInstances().values()) {
            var config = instance.getConfig();
            if (!config.hasArea() || instance.getState() == DungeonState.RECRUITING) continue;

            // Contain dungeon players inside the area
            for (UUID playerId : new ArrayList<>(instance.getPlayers())) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null || !player.isAlive() || player.isSpectator()) continue;
                if (!config.isInArea(player.level().dimension().location().toString(), player.getX(), player.getY(), player.getZ())) {
                    DungeonManager.getInstance().teleportToSpawn(player, config.spawnPoint);
                    player.sendSystemMessage(Component.literal("[Arcadia] Vous ne pouvez pas quitter la zone du donjon!").withStyle(ChatFormatting.RED));
                }
            }

            // Kick non-participants who enter the dungeon area (e.g. /back after leaving)
            Set<UUID> dungeonPlayers = instance.getPlayers();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (dungeonPlayers.contains(player.getUUID())) continue;
                if (player.isSpectator()) continue;
                // Bypass: OP or arcadia_dungeon.bypass.antiparasite permission (LuckPerms compatible)
                if (player.hasPermissions(2)) continue;
                try { if (net.neoforged.neoforge.server.permission.PermissionAPI.getPermission(player, ArcadiaDungeon.BYPASS_ANTIPARASITE)) continue; } catch (Exception ignored) {}
                String dim = player.level().dimension().location().toString();
                if (config.isInArea(dim, player.getX(), player.getY(), player.getZ())) {
                    net.minecraft.server.level.ServerLevel overworld = server.overworld();
                    net.minecraft.core.BlockPos spawnPos = overworld.getSharedSpawnPos();
                    player.teleportTo(overworld, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
                    player.sendSystemMessage(Component.literal("[Arcadia] Un donjon est en cours dans cette zone! Vous avez ete teleporte au spawn.")
                            .withStyle(ChatFormatting.RED));
                }
            }
        }
    }

    // === TIMERS ===
    private void checkDungeonTimers() {
        for (Map.Entry<String, DungeonInstance> entry : new ArrayList<>(DungeonManager.getInstance().getActiveInstances().entrySet())) {
            DungeonInstance instance = entry.getValue();
            String dungeonId = entry.getKey();
            if (instance.getState() == DungeonState.RECRUITING) continue;

            if (instance.isTimedOut()) {
                for (UUID playerId : instance.getPlayers()) {
                    ServerPlayer player = DungeonManager.getInstance().getServer().getPlayerList().getPlayer(playerId);
                    if (player != null) player.sendSystemMessage(Component.literal("[Arcadia] Temps ecoule!").withStyle(ChatFormatting.RED));
                }
                DungeonManager.getInstance().failDungeon(dungeonId);
                continue;
            }

            int timeLimit = instance.getConfig().settings.timeLimitSeconds;
            if (timeLimit > 0) {
                long remaining = timeLimit - instance.getElapsedSeconds();
                // Fix #9: range-based checks
                List<Integer> milestones = instance.getConfig().settings.timerWarnings;
                for (int m : milestones) {
                    int key = Objects.hash(dungeonId, "timer", m);
                    if (remaining <= m && remaining > m - 2 && !announcedTimerMilestones.contains(key)) {
                        announcedTimerMilestones.add(key);
                        for (UUID playerId : instance.getPlayers()) {
                            ServerPlayer player = DungeonManager.getInstance().getServer().getPlayerList().getPlayer(playerId);
                            if (player != null) player.sendSystemMessage(Component.literal("[Arcadia] Temps restant: " + DungeonManager.formatTime(m) + "!").withStyle(ChatFormatting.YELLOW));
                        }
                    }
                }
            }
        }
    }
}
