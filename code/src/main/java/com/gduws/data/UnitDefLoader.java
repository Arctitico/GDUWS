package com.gduws.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.gduws.model.AttackProfile;
import com.gduws.model.MovementType;
import com.gduws.model.ProjectileType;
import com.gduws.model.UnitDef;

/** 从 JSON 文件加载 {@link UnitDef}，并缓存于按 id 索引的注册表。 */
public class UnitDefLoader {

    private final Map<String, UnitDef> registry = new LinkedHashMap<>();

    /** 加载目录下全部 *.json 单位定义。 */
    public void loadDirectory(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                  .sorted()
                  .forEach(this::loadFileQuietly);
        }
    }

    private void loadFileQuietly(Path file) {
        try {
            UnitDef def = loadFile(file);
            registry.put(def.id, def);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load unit def: " + file, e);
        }
    }

    public UnitDef loadFile(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Map<String, Object> root = Json.parseObject(text);
        return fromJson(root);
    }

    @SuppressWarnings("unchecked")
    private UnitDef fromJson(Map<String, Object> root) {
        UnitDef def = new UnitDef();
        def.id = str(root, "id");
        def.displayName = str(root, "displayName");
        def.maxHp = intVal(root, "maxHp");
        def.radius = dbl(root, "radius", 8.0);
        def.movementType = MovementType.valueOf(str(root, "movementType"));
        def.moveSpeed = dbl(root, "moveSpeed", 1.0);
        def.sightRange = intVal(root, "sightRange");
        def.spritePath = root.containsKey("spritePath") ? (String) root.get("spritePath") : null;
        def.turretSpritePath = root.containsKey("turretSpritePath") ? (String) root.get("turretSpritePath") : null;

        Map<String, Object> a = (Map<String, Object>) root.get("attack");
        AttackProfile attack = new AttackProfile();
        if (a != null) {
            attack.canAttackLand = bool(a, "canAttackLand");
            attack.canAttackWaterSurface = bool(a, "canAttackWaterSurface");
            attack.canAttackAir = bool(a, "canAttackAir");
            attack.canAttackUnderwater = bool(a, "canAttackUnderwater");
            attack.maxAttackRange = intVal(a, "maxAttackRange");
            attack.directDamage = intVal(a, "directDamage");
            attack.shootDelay = intVal(a, "shootDelay");
            // 弹种（缺省为子弹）：bullet=快速单体，shell=慢速群体
            String pt = a.containsKey("projectileType") ? a.get("projectileType").toString() : "BULLET";
            attack.projectileType = ProjectileType.valueOf(pt.toUpperCase());
            // 飞行速度缺省：子弹 8.0、炮弹 3.0
            double defSpeed = attack.projectileType == ProjectileType.SHELL ? 3.0 : 8.0;
            attack.projectileSpeed = dbl(a, "projectileSpeed", defSpeed);
            // 群体伤害半径缺省：炮弹 40、子弹 0
            int defSplash = attack.projectileType == ProjectileType.SHELL ? 40 : 0;
            attack.splashRadius = (int) Math.round(dbl(a, "splashRadius", defSplash));
        }
        def.attack = attack;
        return def;
    }

    public UnitDef get(String id) {
        UnitDef def = registry.get(id);
        if (def == null) {
            throw new IllegalArgumentException("Unknown unit id: " + id);
        }
        return def;
    }

    public Map<String, UnitDef> all() {
        return registry;
    }

    // ---- JSON 字段读取辅助 ----

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) {
            throw new IllegalArgumentException("Missing string field: " + key);
        }
        return v.toString();
    }

    private static int intVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (!(v instanceof Number)) {
            throw new IllegalArgumentException("Missing/invalid int field: " + key);
        }
        return (int) Math.round(((Number) v).doubleValue());
    }

    private static double dbl(Map<String, Object> m, String key, double def) {
        Object v = m.get(key);
        return (v instanceof Number) ? ((Number) v).doubleValue() : def;
    }

    private static boolean bool(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Boolean && (Boolean) v;
    }
}
