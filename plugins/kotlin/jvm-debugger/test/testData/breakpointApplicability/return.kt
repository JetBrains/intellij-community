fun basicReturn(x: Int) { /// M
    if (x == 0) return /// L, R
    println(x) /// L
} /// L

fun returnInLambda(x: Int) { /// M
    run { if (x == 2) return@run; println(42) } /// *, L, R, λ
} /// L

// Cannot install return breakpoint when there are several on the line
fun returnInLambdaAndLine(x: Int) { /// M
    run { if (x == 2) return@run; println(42) }; if (x == 3) return /// *, L, λ
} /// L
