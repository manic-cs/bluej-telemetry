package edu.uwi.telemetry.json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * Provides methods for serializing Java values to JSON and parsing
 * JSON text into Java objects
 * 
 * @author Sawdah Kadir
 */
public final class Json {
    private Json() {}

    /**
     * Serializes a Java value into JSON text
     * @param value value to serialize
     * @return JSON representation of the value
     */

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb, false, 0);

        return sb.toString();
    }

    public static String writePretty(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb, true, 0);
        sb.append('\n');

        return sb.toString();
    }

    /**
     * Appends the JSON representation of a value to a StringBuilder
     * @param value value to serialize
     * @param sb destination for the JSON output
     * @param pretty whether to use indentation and line breaks
     * @param depth current indentation depth
     */

    private static void write(Object value, StringBuilder sb, boolean pretty, int depth) {
        if (value == null) {
            sb.append("null");
        }

        else if (value instanceof Boolean || value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            sb.append(value);
        }

        else if (value instanceof Number number) {
            double d = number.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append("null");
            }

            else {
                sb.append(number);
            }
        }

        else if (value instanceof Map<?, ?> map) {
            writeMap(map, sb, pretty, depth);
        }

        else if (value instanceof Collection<?> collection) {
            writeCollection(collection, sb, pretty, depth);
        }

        else {
            writeString(String.valueOf(value), sb);
        }
    }

    /**
     * Serializes a map as a JSON object.
     */

    private static void writeMap(Map<?, ?> map, StringBuilder sb, boolean pretty, int depth) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }

        sb.append('{');
        boolean first = true;

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }

            first = false;
            newline(sb, pretty, depth + 1);
            writeString(String.valueOf(entry.getKey()), sb);
            sb.append(pretty ? ": " : ":");
            write(entry.getValue(), sb, pretty, depth + 1);
        }

        newline(sb, pretty, depth);
        sb.append('}');
    }

    /**
     * Serializes a collection as a JSON array.
     */

    private static void writeCollection(Collection<?> items, StringBuilder sb, boolean pretty, int depth) {
        if (items.isEmpty()) {
            sb.append("[]");
            return;
        }

        sb.append('[');

        boolean first = true;
        for (Object item : items) {
            if (!first) {
                sb.append(',');
            }

            first = false;
            newline(sb, pretty, depth + 1);
            write(item, sb, pretty, depth + 1);
        }

        newline(sb, pretty, depth);
        sb.append(']');
    }

    /**
     * Appends a line break and indentation when pretty printing is enabled.
     */

    private static void newline(StringBuilder sb, boolean pretty, int depth) {
        if (pretty) {
            sb.append('\n');
            sb.append("  ".repeat(depth));
        }
    }

    /**
     * Appends a JSON-escaped string, including its surrounding quotation marks.
     */

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    }

                    else {
                        sb.append(c);
                    }
                }
            }
        }

        sb.append('"');
    }

    /**
     * Parses JSON text into its corresponding Java representation.
     */

    public static Object parse(String text) {
        Parser parser = new Parser(text);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();

        if (!parser.atEnd()) {
            throw new IllegalArgumentException("Unexpected content at position " + parser.pos);
        }

        return value;
    }
    
    /**
     * Parses JSON text and verifies that the result is a JSON object.
     */

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (value instanceof Map<?, ?>) {
            return (Map<String, Object>) value;
        }

        throw new IllegalArgumentException("Not a JSON object");
    }

    /**
     * Parses JSON text by reading its characters sequentially.
     */

    private static final class Parser {
        private final String text;
        private int pos;

        /**
         * Initialises the parser with the supplied JSON text.
         */

        Parser(String text) {
            this.text = text;
        }

        /**
         * Checks whether the parser has reached the end of the input.
         */

        boolean atEnd() {
            return pos >= text.length();
        }

        /**
         * Advances the parser past consecutive whitespace characters.
         */

        void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        /**
         * Reads the next JSON value based on its leading character.
         */

        Object readValue() {
            if (atEnd()) {
                throw new IllegalArgumentException("Unexpected end of input");
            }

            char c = text.charAt(pos);

            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> readNumber();
            };
        }

        /**
         * Matches a JSON literal against the input and advances the parser.
         */

        private Object literal(String word, Object value) {
            if (!text.startsWith(word, pos)) {
                throw new IllegalArgumentException("Invalid literal at position " + pos);
            }

            pos += word.length();

            return value;
        }

        /**
         * Parses a JSON object into a LinkedHashMap.
         */

        private Map<String, Object> readObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++;
            skipWhitespace();

            if (peek() == '}') {
                pos++;
                return map;
            }

            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                map.put(key, readValue());
                skipWhitespace();

                char c = next();
                if (c == '}') {
                    return map;
                }

                if (c != ',') {
                    throw new IllegalArgumentException("Expected ',' or '}' at position " + (pos - 1));
                }
            }
        }

        /**
         * Parses a JSON array into a list of Java values.
         */

        private List<Object> readArray() {
            List<Object> list = new ArrayList<>();
            pos++;
            skipWhitespace();

            if (peek() == ']') {
                pos++;
                return list;
            }

            while (true) {
                skipWhitespace();
                list.add(readValue());
                skipWhitespace();

                char c = next();
                if (c == ']') {
                    return list;
                }

                if (c != ',') {
                    throw new IllegalArgumentException("Expected ',' or ']' at position " + (pos - 1));
                }
            }
        }

        /**
         * Parses a quoted JSON string and processes its escape sequences.
         */

        private String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw new IllegalArgumentException("Unterminated string");
                }

                char c = text.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }

                if (c != '\\') {
                    sb.append(c);
                    continue;
                }

                if (atEnd()) {
                    throw new IllegalArgumentException("Unterminated escape");
                }

                char e = text.charAt(pos++);

                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (pos + 4 > text.length()) {
                            throw new IllegalArgumentException("Bad unicode escape");
                        }

                        sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                    }

                    default -> throw new IllegalArgumentException("Bad escape \\" + e);
                }
            }
        }

        /**
         * Reads a numeric token and converts it into a Java number.
         */

        private Object readNumber() {
            int start = pos;
            while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
                pos++;
            }

            String token = text.substring(start, pos);

            if (token.isEmpty()) {
                throw new IllegalArgumentException("Unexpected character at position " + start);
            }

            try {
                if (token.contains(".") || token.contains("e") || token.contains("E")) {
                    return Double.parseDouble(token);
                }

                return Long.parseLong(token);
            }

            catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Bad number '" + token + "'", ex);
            }
        }

        /**
         * Returns the current input character without advancing the parser.
         */

        private char peek() {
            if (atEnd()) {
                throw new IllegalArgumentException("Unexpected end of input");
            }

            return text.charAt(pos);
        }

        /**
         * Returns the current input character and advances the parser.
         */

        private char next() {
            char c = peek();
            pos++;

            return c;
        }

        /**
         * Reads and verifies that the next character matches the expected character.
         */

        private void expect(char expected) {
            if (next() != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at position " + (pos - 1));
            }
        }
    }
}