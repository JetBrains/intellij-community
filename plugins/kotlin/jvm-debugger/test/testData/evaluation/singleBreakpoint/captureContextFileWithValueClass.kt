@JvmInline
value class IC(val a: Int)

fun main() {
    fun local() = 1
    //Breakpoint!
    val x = 1
}

// EXPRESSION: local() + IC(41).a
// RESULT: 42: I