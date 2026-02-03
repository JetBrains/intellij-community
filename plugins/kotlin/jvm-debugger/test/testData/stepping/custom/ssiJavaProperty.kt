// FILE: ssiJavaProperty.kt
package javaProperty

import forTests.JavaInterface

fun main() {
    val i: JavaInterface = KotlinImpl()
    // SMART_STEP_INTO_BY_INDEX: 1
    //Breakpoint!
    System.out.println(i.str)
}

class KotlinImpl : JavaInterface {
    override fun getStr(): String {
        return "hello"
    }
}

// FILE: forTests/JavaInterface.java
package forTests;

public interface JavaInterface {
    String getStr();
}
