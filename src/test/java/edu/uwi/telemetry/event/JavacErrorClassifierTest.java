package edu.uwi.telemetry.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the classification of common Java compiler errors,
 * case-insensitive matching, and handling of unknown or empty messages.
 *
 * @author Micah Mitchell
 */

class JavacErrorClassifierTest {
    @Test
    void classifiesCommonJavacMessages() {
        assertEquals("Cannot find symbol", JavacErrorClassifier.classify("cannot find symbol"));
        assertEquals("Syntax error", JavacErrorClassifier.classify("';' expected"));
        assertEquals("Type mismatch", JavacErrorClassifier.classify("incompatible types: String cannot be converted to int"));
        assertEquals("Missing return", JavacErrorClassifier.classify("missing return statement"));
        assertEquals("Uninitialised variable", JavacErrorClassifier.classify("variable x might not have been initialized"));
        assertEquals("Unbalanced braces", JavacErrorClassifier.classify("reached end of file while parsing"));
        assertEquals("Unhandled exception", JavacErrorClassifier.classify("unreported exception IOException; must be caught"));
        assertEquals("Wrong method arguments", JavacErrorClassifier.classify("method foo in class A cannot be applied to given types"));
        assertEquals("Class and file name mismatch", JavacErrorClassifier.classify("class Foo is public, should be declared in a file named Foo.java"));
    }

    @Test
    void isCaseInsensitive() {
        assertEquals("Cannot find symbol", JavacErrorClassifier.classify("CANNOT FIND SYMBOL"));
    }

    @Test
    void unknownOrEmptyMessagesAreOther() {
        assertEquals(JavacErrorClassifier.OTHER, JavacErrorClassifier.classify("something unusual"));
        assertEquals(JavacErrorClassifier.OTHER, JavacErrorClassifier.classify(""));
        assertEquals(JavacErrorClassifier.OTHER, JavacErrorClassifier.classify(null));
    }
}