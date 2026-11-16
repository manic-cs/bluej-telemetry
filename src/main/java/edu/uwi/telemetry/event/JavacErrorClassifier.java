package edu.uwi.telemetry.event;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Classifies Java compiler errors into predefined categories
 * by matching compiler messages against regular expression patterns.
 * Returns "Other" when no matching category is found or when
 * the compiler message is null or blank.
 *
 * @author Micah Mitchell
 */

public final class JavacErrorClassifier {
    public static final String OTHER = "Other";

    private record Rule(Pattern pattern, String category) {
        static Rule of(String regex, String category) {
            return new Rule(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), category);
        }
    }

    private static final List<Rule> RULES = List.of(
            Rule.of("reached end of file while parsing|class, interface, enum, or record expected", "Unbalanced braces"),
            Rule.of("unclosed (string|character) literal|unclosed comment", "Unclosed literal"),
            Rule.of("cannot find symbol|cannot resolve symbol|package .* does not exist", "Cannot find symbol"),
            Rule.of("incompatible types|possible lossy conversion|bad operand types", "Type mismatch"),
            Rule.of("missing return statement", "Missing return"),
            Rule.of("might not have been initialized", "Uninitialised variable"),
            Rule.of("cannot be applied to|no suitable (method|constructor)|actual and formal argument", "Wrong method arguments"),
            Rule.of("unreported exception", "Unhandled exception"),
            Rule.of("is already defined", "Duplicate declaration"),
            Rule.of("non-static .* cannot be referenced", "Static context error"),
            Rule.of("should be declared in a file named", "Class and file name mismatch"),
            Rule.of("has private access|is not public|has protected access", "Access violation"),
            Rule.of("unreachable statement", "Unreachable code"),
            Rule.of("is abstract; cannot be instantiated|does not override abstract method", "Abstract class misuse"),
            Rule.of("'.+' expected|<identifier> expected|illegal start of|not a statement|else without if", "Syntax error"));

    private JavacErrorClassifier() {
    }

    public static String classify(String compilerMessage) {
        if (compilerMessage == null || compilerMessage.isBlank()) {
            return OTHER;
        }

        String message = compilerMessage.toLowerCase(Locale.ROOT);

        for (Rule rule : RULES) {
            if (rule.pattern().matcher(message).find()) {
                return rule.category();
            }
        }

        return OTHER;
    }
}