// FILE: lib/JavaMethods.java
package lib;

public class JavaMethods {
    private static double s1(int a, Long b, String c) { return -1.0; }
    static String s2() { return "foo"; }
    private static int s3(char c, String s, boolean b) { return 2; }

    private long i1(int a, Long b, String c) { return -2; }
    String i2() { return "bar"; }
    private int i3(char c, String s, boolean b) { return 4; }
}

// FILE: test.kt
package foo

import lib.JavaMethods

fun main() {
    val m = JavaMethods()
    //Breakpoint!
    val a = 0
}

fun <T> block(block: () -> T): T {
    return block()
}

// EXPRESSION: block { JavaMethods.s1(1, 2, "c") }
// RESULT: -1.0: D

// EXPRESSION: block { JavaMethods.s2() }
// RESULT: "foo": Ljava/lang/String;

// EXPRESSION: block { JavaMethods.s3('c', "s", true) }
// RESULT: 2: I

// EXPRESSION: block { m.i1(5, 10L, "x") }
// RESULT: -2: J

// EXPRESSION: block { m.i2() }
// RESULT: "bar": Ljava/lang/String;

// EXPRESSION: block { m.i3('q', "z", false) }
// RESULT: 4: I
