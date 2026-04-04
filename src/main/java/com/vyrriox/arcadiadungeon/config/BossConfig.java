package com.vyrriox.arcadiadungeon.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BossConfig {
    public String id = "";
    public String entityType = "minecraft:zombie";
    public String customName = "Boss";
    public double baseHealth = 100;
    public double baseDamage = 10;
    public boolean adaptivePower = true;
    public double healthMultiplierPerPlayer = 0.5;
    public double damageMultiplierPerPlayer = 0.1;
    public SpawnPointConfig spawnPoint = new SpawnPointConfig();
    public List<PhaseConfig> phases = new ArrayList<>();
    public List<RewardConfig> rewards = new ArrayList<>();
    public boolean showBossBar = true;
    public String bossBarColor = "RED";
    public boolean optional = false;
    public double spawnChance = 1.0;
    public int spawnAfterWave = 0; // 0 = spawn after all waves (default), N = spawn after wave N

    // Boss equipment
    public String mainHand = "";
    public String offHand = "";
    public String helmet = "";
    public String chestplate = "";
    public String leggings = "";
    public String boots = "";

    // Custom attributes (e.g. "minecraft:generic.armor" -> 10.0)
    public Map<String, Double> customAttributes = new HashMap<>();

    public BossConfig() {}

    public BossConfig(String id, String entityType, double baseHealth, double baseDamage) {
        this.id = id;
        this.entityType = entityType;
        this.baseHealth = baseHealth;
        this.baseDamage = baseDamage;
    }
}
