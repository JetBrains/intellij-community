package breakpointsLambdaProperties

//Breakpoint! (lambdaOrdinal = 1)
val gF1: (Int, Int) -> String = { a, b -> "Both ${a + b}" }

class A1 {
    //Breakpoint! (lambdaOrdinal = 1)
    val mF1: (Int, Int) -> String = { a, b -> "Both ${a + b}" }
}

fun f1() {
    //Breakpoint! (lambdaOrdinal = 1)
    val lF1: (Int, Int) -> String = { a, b -> "Both ${a + b}" }
    lF1(1, 2)
}

fun main() {
    gF1(1, 2)
    A1().mF1(1, 3)
    f1()
}

// RESUME: 2
