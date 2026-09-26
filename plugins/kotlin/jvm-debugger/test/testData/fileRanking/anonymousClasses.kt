// IGNORE_K2
// IGNORE_K2_K1
//FILE: a/a.kt
// DISABLE_STRICT_MODE
package a

abstract class R {
    abstract fun run()
}

fun eval(r: R) {
    r.run()
}

class Some {
    fun foo() {
        eval(object : R() {      // Line with negative score
            override fun run() {
                val a = 1        // R: 4 L: 19
                val b = 12       // R: 4 L: 20
            }
        })
    }
}
// PRODUCED_CLASS_NAMES: a.AKt, a.R, a.Some, a.Some$foo$1