package breakpointInLambdaWithNestedLambda

fun Any.lambda(l: () -> Unit) = l()
fun Any.lambda2(l: () -> Unit) = l()
fun consume(x: Int) = println(x)
fun consume2(x: Int) = println(x)

fun main() {
    ""
        .lambda { consume(0) }
        .lambda { consume(1) }
        .lambda { consume(2) }
        .lambda { consume(3) }
        .lambda { consume(4) }
        .lambda { consume(5) }
        .lambda { consume(6) }
        .lambda { consume(7) }
        .lambda { consume(8) }
        // STEP_INTO: 1
        //Breakpoint!, lambdaOrdinal = 1
        .lambda { consume(9) }.lambda2 { consume2(10) }
}
