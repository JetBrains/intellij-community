// FILE: smartStepIntoClassMethodReference.kt
package smartStepIntoClassMethodReference

import forTests.JavaUtil

fun main() {
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = -1
    JavaUtil.lambdaFun {
        println()
    }

    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = -1
    JavaUtil.lambdaGenericFun {
        println(it)
        it + 1
    }
}

// FILE: forTests/JavaUtil.java
package forTests;

import java.util.function.Function;

public class JavaUtil {
    public static void lambdaFun(Runnable r) {
        r.run();
    }

    public static void lambdaGenericFun(Function<? super Integer, ? extends Integer> r) {
        r.apply(1);
    }
}
