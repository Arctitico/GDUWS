package com.gduws.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gduws.model.Decoration;
import com.gduws.model.GameMap;
import com.gduws.model.LevelDef;
import com.gduws.model.MovementType;
import com.gduws.model.ProjectileType;
import com.gduws.model.TerrainType;
import com.gduws.model.UnitDef;
import com.gduws.model.UnitRole;

/**
 * 数据层加载器单元测试：{@link UnitDefLoader} / {@link MapLoader} / {@link LevelLoader}。
 *
 * <p>关联：FR-01（关卡加载）、NFR-04（数据文件容错——缺失/格式错误时抛出明确异常，不静默）、
 * §5.4 关卡定义、§5.5 地图文件格式。</p>
 */
@DisplayName("数据加载器")
class DataLoadersTest {

    private static Path write(Path dir, String name, String content) throws IOException {
        Path p = dir.resolve(name);
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    @Nested
    @DisplayName("UnitDefLoader 单位定义")
    class UnitDefLoaderTests {

        @Test
        @DisplayName("加载合法单位定义，字段正确")
        void loadsValidUnit(@TempDir Path tmp) throws IOException {
            Path f = write(tmp, "u.json", """
                { "id":"light_tank", "displayName":"轻型坦克", "maxHp":210, "radius":11,
                  "movementType":"LAND", "moveSpeed":1.1, "sightRange":120,
                  "attack": { "canAttackLand":true, "canAttackWaterSurface":true,
                              "maxAttackRange":130, "directDamage":25, "shootDelay":75 } }
                """);
            UnitDef d = new UnitDefLoader().loadFile(f);

            assertEquals("light_tank", d.id);
            assertEquals(210, d.maxHp);
            assertEquals(MovementType.LAND, d.movementType);
            assertEquals(120, d.sightRange);
            assertEquals(130, d.attack.maxAttackRange);
            assertTrue(d.attack.canAttackLand);
            assertFalse(d.attack.canAttackAir);
        }

        @Test
        @DisplayName("弹种默认值：bullet 速度 8/无溅射，shell 速度 3/溅射 40")
        void projectileDefaults(@TempDir Path tmp) throws IOException {
            UnitDef bullet = new UnitDefLoader().loadFile(write(tmp, "b.json", """
                { "id":"b", "displayName":"b", "maxHp":10, "movementType":"LAND", "sightRange":50,
                  "attack": { "canAttackLand":true, "maxAttackRange":10, "directDamage":1, "shootDelay":1 } }
                """));
            assertEquals(ProjectileType.BULLET, bullet.attack.projectileType);
            assertEquals(8.0, bullet.attack.projectileSpeed, 1e-9);
            assertEquals(0, bullet.attack.splashRadius);

            UnitDef shell = new UnitDefLoader().loadFile(write(tmp, "s.json", """
                { "id":"s", "displayName":"s", "maxHp":10, "movementType":"LAND", "sightRange":50,
                  "attack": { "canAttackLand":true, "maxAttackRange":10, "directDamage":1, "shootDelay":1,
                              "projectileType":"shell" } }
                """));
            assertEquals(ProjectileType.SHELL, shell.attack.projectileType);
            assertEquals(3.0, shell.attack.projectileSpeed, 1e-9);
            assertEquals(40, shell.attack.splashRadius);
        }

        @Test
        @DisplayName("NFR-04：缺失必填字段抛出明确异常")
        void missingRequiredFieldThrows(@TempDir Path tmp) throws IOException {
            Path f = write(tmp, "bad.json", """
                { "id":"x", "displayName":"x", "movementType":"LAND", "sightRange":100 }
                """); // 缺 maxHp
            UnitDefLoader loader = new UnitDefLoader();
            assertThrows(IllegalArgumentException.class, () -> loader.loadFile(f));
        }

        @Test
        @DisplayName("查询未知单位 id 抛出异常")
        void unknownIdThrows() {
            assertThrows(IllegalArgumentException.class, () -> new UnitDefLoader().get("nope"));
        }
    }

    @Nested
    @DisplayName("MapLoader 地图文件（§5.5）")
    class MapLoaderTests {

        @Test
        @DisplayName("解析三层字符网格地图：地形 / 装饰 / 禁布")
        void parsesLayeredMap(@TempDir Path tmp) throws IOException {
            Path f = write(tmp, "m.map", """
                5 3 20
                [terrain]
                .~#s,
                .....
                =____
                [decoration]
                T....
                .....
                .....
                [deploy]
                XX...
                .....
                ....X
                """);
            GameMap m = new MapLoader().loadFile(f);

            assertEquals(5, m.cols);
            assertEquals(3, m.rows);
            assertEquals(20, m.tileSize);

            assertEquals(TerrainType.GRASS, m.tileAt(0, 0).terrain);
            assertEquals(TerrainType.WATER, m.tileAt(1, 0).terrain);
            assertEquals(TerrainType.MOUNTAIN, m.tileAt(2, 0).terrain);
            assertEquals(TerrainType.SAND, m.tileAt(3, 0).terrain);
            assertEquals(TerrainType.DIRT, m.tileAt(4, 0).terrain);
            assertEquals(TerrainType.DEEP, m.tileAt(0, 2).terrain);
            assertEquals(TerrainType.SHALLOW, m.tileAt(1, 2).terrain);

            assertEquals(Decoration.TREE, m.tileAt(0, 0).decoration);

            assertTrue(m.isDeployForbidden(0, 0), "X 标记 → 禁布");
            assertTrue(m.isDeployForbidden(1, 0));
            assertFalse(m.isDeployForbidden(2, 0));
            assertTrue(m.isDeployForbidden(4, 2));
        }

        @Test
        @DisplayName("兼容旧版仅含地形单层的地图")
        void parsesLegacySingleLayer(@TempDir Path tmp) throws IOException {
            GameMap m = new MapLoader().loadFile(write(tmp, "legacy.map", """
                3 2 20
                ...
                ,,,
                """));
            assertEquals(3, m.cols);
            assertEquals(2, m.rows);
            assertEquals(TerrainType.GRASS, m.tileAt(0, 0).terrain);
            assertEquals(TerrainType.DIRT, m.tileAt(0, 1).terrain);
        }

        @Test
        @DisplayName("NFR-04：缺失头行 → IOException")
        void missingHeaderThrows(@TempDir Path tmp) throws IOException {
            Path f = write(tmp, "noheader.map", "# only a comment\n\n");
            assertThrows(IOException.class, () -> new MapLoader().loadFile(f));
        }

        @Test
        @DisplayName("NFR-04：地形行数少于声明 → IOException")
        void fewerTerrainRowsThrows(@TempDir Path tmp) throws IOException {
            Path f = write(tmp, "short.map", """
                5 3 20
                [terrain]
                .....
                .....
                """); // 声明 3 行只给 2 行
            assertThrows(IOException.class, () -> new MapLoader().loadFile(f));
        }
    }

    @Nested
    @DisplayName("LevelLoader 关卡定义（§5.4）")
    class LevelLoaderTests {

        @Test
        @DisplayName("解析关卡：预算、敌方预置、角色默认 STRIKE")
        void parsesLevel(@TempDir Path tmp) throws IOException {
            Path f = write(tmp, "lvl.json", """
                {
                  "id": "lvl_test",
                  "name": "测试关",
                  "map": "data/maps/x.map",
                  "playerBudget": { "light_tank": 5, "interceptor": 2 },
                  "enemyUnits": [
                    { "unitId": "light_tank", "col": 10, "row": 20, "role": "SCOUT" },
                    { "unitId": "heavy_tank", "col": 30, "row": 5 }
                  ]
                }
                """);
            LevelDef level = new LevelLoader().loadFile(f);

            assertEquals("lvl_test", level.id);
            assertEquals("测试关", level.name);
            assertEquals("data/maps/x.map", level.mapPath);
            assertEquals(5, level.playerBudget.get("light_tank"));
            assertEquals(2, level.playerBudget.get("interceptor"));

            assertEquals(2, level.enemyUnits.size());
            LevelDef.PlacedUnit first = level.enemyUnits.get(0);
            assertEquals("light_tank", first.unitId);
            assertEquals(10, first.col);
            assertEquals(20, first.row);
            assertEquals(UnitRole.SCOUT, first.role);
            assertEquals(UnitRole.STRIKE, level.enemyUnits.get(1).role, "未指定 role 默认打击");
        }
    }
}
