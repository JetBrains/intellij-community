// FILE: smartStepIntoClassMethodReference.kt
package smartStepIntoClassMethodReference

import forTests.JavaUtil

fun main() {
    // SMART_STEP_INTO_BY_INDEX: 2
    //Breakpoint!
    JavaUtil.lambdaFun {
        println()
    }
}

// FILE: forTests/JavaUtil.java
package forTests;

public class JavaUtil {
    public static void lambdaFun(Runnable r) {
        r.run();
    }
}
