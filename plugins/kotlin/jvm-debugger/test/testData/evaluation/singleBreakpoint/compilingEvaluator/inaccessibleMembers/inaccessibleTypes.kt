// FILE: lib/JavaFields.java
package lib;

import java.util.*;

public class JavaFields {
    private static boolean b = false;
    private static byte by = 1;
    private static short sh = 2;
    private static int i = 3;
    private static char c = 'x';
    private static long l = 100L;
    private static float f = 5.2f;
    private static double d = -10.4;

    private static boolean[] ba = new boolean[] { false, true };

    private static String s = "foo";
}

// FILE: test.kt
import lib.JavaFields


fun main() {
    //Breakpoint!
    val x = 0
}

fun <T> block(block: () -> T): T {
    return block()
}

// EXPRESSION: block { JavaFields.b = true }
// RESULT: VOID_VALUE

// EXPRESSION: block { JavaFields.b }
// RESULT: 1: Z

// EXPRESSION: block { JavaFields.by = 2 }
// RESULT: VOID_VALUE

// EXPRESSION: block { JavaFields.by }
// RESULT: 2: B

// EXPRESSION: block { JavaFields.sh = 4 }
// RESULT: VOID_VALUE

// EXPRESSION: block { JavaFields.sh }
// RESULT: 4: S

// EXPRESSION: block { JavaFields.i = 6 }
// RESULT: VOID_VALUE

// EXPRESSION: block { JavaFields.i }
// RESULT: 6: I

// EXPRESSION: block { JavaFields.c = 'X' }
// RESULT: VOID_VALUE

// EXPRESSION: block { JavaFields.c }
// RESULT: 88: C

// EXPRESSION: block { JavaFields.l = 200 }
// RESULT: VOID_VALUE

// EXPRESSION: block { JavaFields.l }
// RESULT: 200: J

// EXPRESSION: block { JavaFields.f = 10.4f }
// RESULT: VOID_VALUE

// EXPRESSION: block { JavaFields.f }
// RESULT: 10.4: F

// EXPRESSION: block { JavaFields.d = -20.8 }
// RESULT: VOID_VALUE

// EXPRESSION: block { JavaFields.d }
// RESULT: -20.8: D

// EXPRESSION: block { JavaFields.ba }
// RESULT: instance of boolean[2] (id=ID): [Z