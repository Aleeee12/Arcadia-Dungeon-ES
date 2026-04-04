package com.vyrriox.arcadiadungeon.config;

import java.util.ArrayList;
import java.util.List;

public class DungeonConfig {
    public String id = "";
    public String name = "";
    public int cooldownSeconds = 3600;
    public boolean announceStart = true;
    public boolean announceCompletion = true;
    public String startMessage = "&6[Donjon] &e%player% &7lance &e%dungeon%&7! Rejoignez avec &f/arcadia_dungeon join %id%&7!";
    public String completionMessage = "&6[Donjon] &a%player% &7a vaincu &e%dungeon%&7!";
    public String failMessage = "&6[Donjon] &c%player% &7a echoue dans &e%dungeon%&7!";
    public boolean teleportBackOnComplete = true;
    public SpawnPointConfig spawnPoint = new SpawnPointConfig();
    public List<BossConfig> bosses = new ArrayList<>();
    public List<RewardConfig> completionRewards = new ArrayList<>();
    public DungeonSettings settings = new DungeonSettings();
    public boolean enabled = true;
    public int availableEverySeconds = 0;
    public boolean announceAvailability = true;
    public String availabilityMessage = "&6[Donjon] &e%dungeon% &7est de nouveau accessible! &fLancez avec &e/arcadia_dungeon start %id%";
    public String requiredPermission = "";
    public int order = 0;
    public String requiredDungeon = "";
    public List<WaveConfig> waves = new ArrayList<>();

    // Recruitment / group system
    public int recruitmentDurationSeconds = 90;
    public String recruitmentMessage = "&6[Donjon] &e%player% &7ouvre &e%dungeon%&7! &fRejoignez dans les %time%s &7avec &f/arcadia_dungeon join %id%";

    // Dungeon area (cuboid) - containment + anti-parasites
    public AreaPos areaPos1 = null;
    public AreaPos areaPos2 = null;

    public static class AreaPos {
        public String dimension = "minecraft:overworld";
        public int x = 0;
        public int y = 0;
        public int z = 0;

        public AreaPos() {}

        public AreaPos(String dimension, int x, int y, int z) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public boolean hasArea() {
        return areaPos1 != null && areaPos2 != null;
    }

    public boolean isInArea(String dimension, double px, double py, double pz) {
        if (!hasArea()) return false;
        if (!areaPos1.dimension.equals(dimension)) return false;

        int minX = Math.min(areaPos1.x, areaPos2.x);
        int maxX = Math.max(areaPos1.x, areaPos2.x);
        int minY = Math.min(areaPos1.y, areaPos2.y);
        int maxY = Math.max(areaPos1.y, areaPos2.y);
        int minZ = Math.min(areaPos1.z, areaPos2.z);
        int maxZ = Math.max(areaPos1.z, areaPos2.z);

        return px >= minX && px <= maxX + 1
                && py >= minY && py <= maxY + 1
                && pz >= minZ && pz <= maxZ + 1;
    }

    public DungeonConfig() {}

    public DungeonConfig(String id, String name) {
        this.id = id;
        this.name = name;
    }
}
