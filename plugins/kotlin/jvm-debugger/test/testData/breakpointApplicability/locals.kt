fun foo1() { /// M
    // We don't support function breakpoints for local functions yet
    fun local() { /// L
        println() /// L
    } /// L
} /// L

fun foo2() { /// M
    val local = fun() { /// *, L, λ
        println() /// L
    } /// L
} /// L

fun foo3() { /// M
    val local = { /// *, L, λ
        println() /// L
    } /// L
} /// L

fun foo4() { /// M
    fun local(block: () -> Unit = { println() }) {} /// *, L, λ
} /// L
