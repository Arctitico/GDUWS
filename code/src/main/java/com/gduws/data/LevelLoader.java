package com.gduws.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.gduws.model.LevelDef;
import com.gduws.model.UnitRole;

/** 从 JSON 加载 {@link LevelDef}。 */
public class LevelLoader {

    @SuppressWarnings("unchecked")
    public LevelDef loadFile(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Map<String, Object> root = Json.parseObject(text);

        LevelDef level = new LevelDef();
        level.id = (String) root.get("id");
        level.name = (String) root.get("name");
        level.mapPath = (String) root.get("map");

        Map<String, Object> budget = (Map<String, Object>) root.get("playerBudget");
        if (budget != null) {
            for (Map.Entry<String, Object> e : budget.entrySet()) {
                level.playerBudget.put(e.getKey(), (int) Math.round(((Number) e.getValue()).doubleValue()));
            }
        }

        List<Object> enemies = (List<Object>) root.get("enemyUnits");
        if (enemies != null) {
            for (Object o : enemies) {
                Map<String, Object> e = (Map<String, Object>) o;
                String unitId = (String) e.get("unitId");
                int col = (int) Math.round(((Number) e.get("col")).doubleValue());
                int row = (int) Math.round(((Number) e.get("row")).doubleValue());
                UnitRole role = e.containsKey("role")
                    ? UnitRole.valueOf(((String) e.get("role")).toUpperCase())
                    : UnitRole.STRIKE;
                level.enemyUnits.add(new LevelDef.PlacedUnit(unitId, col, row, role));
            }
        }
        return level;
    }
}
