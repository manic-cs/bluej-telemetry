package edu.uwi.telemetry.json;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests JSON serialization, deserialization, formatting and error handling.
 *
 * @author Sawdah Kadir
 */
class JsonTest {
    @Test
    void writesAndParsesNestedStructures() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "Blue");
        map.put("count", 3);
        map.put("ratio", 0.5);
        map.put("ok", true);
        map.put("none", null);
        map.put("list", List.of(1, "two", Map.of("k", "v")));

        Map<String, Object> parsed = Json.parseObject(Json.write(map));

        assertEquals("Blue", parsed.get("name"));
        assertEquals(3L, parsed.get("count"));
        assertEquals(0.5, parsed.get("ratio"));
        assertEquals(true, parsed.get("ok"));
        assertTrue(parsed.containsKey("none"));
        assertEquals(List.of(1L, "two", Map.of("k", "v")), parsed.get("list"));
    }

    @Test
    void escapesSpecialCharacters() {
        String tricky = "line1\nline2\t\"quoted\" back\\slash \u0001";
        String json = Json.write(Map.of("text", tricky));
        assertEquals(tricky, Json.parseObject(json).get("text"));
    }

    @Test
    void prettyOutputIsStillValid() {
        Map<String, Object> map = Map.of("a", List.of(1, 2), "b", Map.of());
        String pretty = Json.writePretty(map);
        assertTrue(pretty.contains("\n"));
        assertEquals(Json.parseObject(Json.write(map)), Json.parseObject(pretty));
    }

    @Test
    void nonFiniteNumbersBecomeNull() {
        assertEquals("null", Json.write(Double.NaN));
        assertEquals("null", Json.write(Double.POSITIVE_INFINITY));
    }

    @Test
    void parsesUnicodeEscapes() {
        assertEquals("A", Json.parse("\"\\u0041\""));
    }

    @Test
    void rejectsMalformedInput() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":1} extra"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1,2"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("\"unterminated"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("nope"));
        assertThrows(IllegalArgumentException.class, () -> Json.parseObject("[1]"));
    }
}