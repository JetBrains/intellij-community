// ATTACH_LIBRARY: maven(org.jetbrains.kotlin:kotlin-script-runtime:1.6.21)
// FILE: kt51755.kts
package kt51755

val n = 12

fun <T> id(x: T):T = x

// EXPRESSION: id(n) + 30
// RESULT: 42: I
//Breakpoint!
val str = "42"

class A(val x: String) {
    fun sum(y: Int): Int {
        // EXPRESSION: y + sum(11) + x.length
        // RESULT: 31: I
        //Breakpoint!
        val r = y + x.length
        // EXPRESSION: this
        // RESULT: instance of kt51755.Kt51755$A(id=ID): Lkt51755/Kt51755$A;
        //Breakpoint!
        return r
    }
}
A("hoho").sum(12)

// IGNORE_LOG_ERRORS

// See KT-74004