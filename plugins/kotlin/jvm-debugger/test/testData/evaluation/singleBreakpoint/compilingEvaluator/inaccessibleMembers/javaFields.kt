// FILE: lib/JavaFields.java
package lib;

public class JavaFields {
    private static String s = "s";
    static int i = 1;
    private static double d = 2.0;
    private static boolean b = true;

    String is = "is";
    private int ii = 2;
    private double id = 3.0;
    private boolean ib = false;

    public JavaFields() {}
}

// FILE: test.kt
package foo

import lib.JavaFields

fun main() {
    val f = JavaFields()
    //Breakpoint!
    val a = 0
}

fun <T> block(block: () -> T): T {
    return block()
}

// EXPRESSION: block { JavaFields.s = "ss" }
// RESULT: VOID_VALUE

// EXPRESSION: block { JavaFields.s }
// RESULT: "ss": Ljava/lang/String;

// EXPRESSION: block { JavaFields.i = 2 }
// RESULT: VOID_VALUE

// EXPRESSION: block { JavaFields.i }
// RESULT: 2: I

// EXPRESSION: block { JavaFields.d = -4.0 }
// RESULT: VOID_VALUE

// EXPRESSION: block { JavaFields.d }
// RESULT: -4.0: D

// EXPRESSION: block { JavaFields.b = false }
// RESULT: VOID_VALUE

// EXPRESSION: block { JavaFields.b }
// RESULT: 0: Z

// EXPRESSION: block { f.`is` = "isis" }
// RESULT: VOID_VALUE

// EXPRESSION: block { f.`is` }
// RESULT: "isis": Ljava/lang/String;

// EXPRESSION: block { f.ii = 4 }
// RESULT: VOID_VALUE

// EXPRESSION: block { f.ii }
// RESULT: 4: I

// EXPRESSION: block { f.id = 6.0 }
// RESULT: VOID_VALUE

// EXPRESSION: block { f.id }
// RESULT: 6.0: D

// EXPRESSION: block { f.ib = true }
// RESULT: VOID_VALUE

// EXPRESSION: block { f.ib }
// RESULT: 1: Z