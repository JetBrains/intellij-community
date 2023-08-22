// FILE: breakpointInCrossinlineLambdaWithImport.kt

package breakpointInCrossinlineLambdaWithImport

import breakpointInCrossinlineLambdaWithImport.funs.foo as importedFoo

fun main() {
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 1
    importedFoo { f() }

    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 2
    importedFoo { f() }; importedFoo { g() }
}

fun f() = Unit
fun g() = Unit

// FILE: breakpointInCrossinlineLambdaWithImport.Funs.kt
package breakpointInCrossinlineLambdaWithImport.funs

inline fun foo(crossinline body: () -> Unit) {
    foo(1, body)
}

inline fun foo(x: Int, crossinline body: () -> Unit) {
    val runnable = object : Runnable {
        override fun run() = body()
    }
    runnable.run()
}
