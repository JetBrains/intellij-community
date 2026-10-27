// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// FILE: JavaClass.java
package javaStatic;

public class JavaClass {
    public static int FIELD = 7;

    public static int method() {
        return 9;
    }
}

// FILE: javaStatic.kt
package javaStatic

fun main(args: Array<String>) {
    val x: Any = JavaClass()
    //Breakpoint!
    println(x)
}

// EXPRESSION: x.FIELD
// RESULT: 7: I

// EXPRESSION: x.method()
// RESULT: 9: I
