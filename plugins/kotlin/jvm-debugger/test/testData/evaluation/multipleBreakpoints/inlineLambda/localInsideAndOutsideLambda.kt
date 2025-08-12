// IGNORE_K1

fun main() {
    fun localOutsideLambda(x: Int) = x + 1
    foo {
        class X {
            fun memberInLocalClass(x: Int) = x + 2
        }
        fun localInsideLambda(x : Int) = x + 3
        localInsideLambda(it) + localOutsideLambda(it) + X().memberInLocalClass(it)
    }
}

inline fun foo(block: (Int) -> Int) {
    // EXPRESSION: block(1)
    // RESULT: 9: I
    //Breakpoint!
    val x = 1
}