package com.gduws.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link Json} 手写递归下降解析器单元测试。
 *
 * <p>覆盖：基本类型、嵌套结构、转义、容错（畸形输入抛 {@code IllegalArgumentException}）。
 * 关联需求：约束"手写 JSON 解析器，无第三方依赖"、NFR-04 数据文件容错。</p>
 */
@DisplayName("Json 解析器")
class JsonTest {

    @Nested
    @DisplayName("基本类型")
    class Primitives {

        @Test
        void parsesNumberAsDouble() {
            Object v = Json.parse("123");
            assertInstanceOf(Double.class, v);
            assertEquals(123.0, (Double) v, 1e-9);
        }

        @Test
        void parsesNegativeAndExponentNumbers() {
            assertEquals(-1500.0, (Double) Json.parse("-1.5e3"), 1e-9);
            assertEquals(0.25, (Double) Json.parse("0.25"), 1e-9);
        }

        @Test
        void parsesStringBooleanNull() {
            assertEquals("hello", Json.parse("\"hello\""));
            assertEquals(Boolean.TRUE, Json.parse("true"));
            assertEquals(Boolean.FALSE, Json.parse("false"));
            assertNull(Json.parse("null"));
        }

        @Test
        void parsesStringEscapesAndUnicode() {
            assertEquals("line\nbreak\ttab", Json.parse("\"line\\nbreak\\ttab\""));
            assertEquals("quote\"slash/", Json.parse("\"quote\\\"slash\\/\""));
            assertEquals("A", Json.parse("\"\\u0041\""));
        }

        @Test
        void skipsSurroundingWhitespace() {
            Object v = Json.parse("   {  }  ");
            assertInstanceOf(Map.class, v);
            assertTrue(((Map<?, ?>) v).isEmpty());
        }
    }

    @Nested
    @DisplayName("复合结构")
    class Composite {

        @Test
        @SuppressWarnings("unchecked")
        void parsesNestedObjectAndArray() {
            Map<String, Object> root = Json.parseObject(
                "{\"id\":\"u1\",\"hp\":210,\"flags\":[true,false,null],"
                + "\"attack\":{\"range\":130,\"dmg\":25}}");

            assertEquals("u1", root.get("id"));
            assertEquals(210.0, (Double) root.get("hp"), 1e-9);

            List<Object> flags = (List<Object>) root.get("flags");
            assertEquals(3, flags.size());
            assertEquals(Boolean.TRUE, flags.get(0));
            assertNull(flags.get(2));

            Map<String, Object> attack = (Map<String, Object>) root.get("attack");
            assertEquals(130.0, (Double) attack.get("range"), 1e-9);
        }

        @Test
        void parsesEmptyContainers() {
            assertTrue(((Map<?, ?>) Json.parse("{}")).isEmpty());
            assertTrue(((List<?>) Json.parse("[]")).isEmpty());
        }

        @Test
        void preservesObjectInsertionOrder() {
            Map<String, Object> root = Json.parseObject("{\"b\":1,\"a\":2,\"c\":3}");
            assertEquals(List.of("b", "a", "c"), List.copyOf(root.keySet()));
        }
    }

    @Nested
    @DisplayName("容错（畸形输入抛异常，不静默吞掉）")
    class Errors {

        @Test
        void rejectsTrailingCharacters() {
            assertThrows(IllegalArgumentException.class, () -> Json.parse("1 2"));
        }

        @Test
        void rejectsUnterminatedString() {
            assertThrows(IllegalArgumentException.class, () -> Json.parse("\"abc"));
        }

        @Test
        void rejectsUnexpectedCharacter() {
            assertThrows(IllegalArgumentException.class, () -> Json.parse("@"));
        }

        @Test
        void rejectsEmptyInput() {
            assertThrows(IllegalArgumentException.class, () -> Json.parse("   "));
        }

        @Test
        void rejectsBadLiteral() {
            assertThrows(IllegalArgumentException.class, () -> Json.parse("tru"));
        }

        @Test
        void parseObjectRejectsNonObjectTopLevel() {
            assertThrows(IllegalArgumentException.class, () -> Json.parseObject("[1,2,3]"));
        }

        @Test
        void rejectsMissingComma() {
            assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":1 \"b\":2}"));
        }
    }
}
