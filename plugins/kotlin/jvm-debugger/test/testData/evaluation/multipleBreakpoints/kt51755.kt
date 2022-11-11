// ATTACH_LIBRARY: maven("org.jetbrains.kotlin:kotlin-script-runtime:1.6.21")


// FILE: kt51755/some.kts
package kt51755

val n = 12

fun <T> id(x: T):T = x

class A(val x: String) {
    fun sum(y: Int): Int {
        //Breakpoint1
        return y + x.length
    }
}
//Breakpoint2
val str = "42"


// FILE: test.kt
import kt51755.*

// ADDITIONAL_BREAKPOINT: some.kts / Breakpoint2 / line

// EXPRESSION: id(n) + 30
// RESULT: 42: I


// ADDITIONAL_BREAKPOINT: some.kts / Breakpoint1 / line

// EXPRESSION: y + sum(11) + x.length
// RESULT: 31: I

// EXPRESSION: this
// RESULT: instance of kt51755.Some$A(id=ID): Lkt51755/Some$A;


fun main() {
    val some = Some()
    val a = Some.A("hoho")
    a.sum(12)
}



