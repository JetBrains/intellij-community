// WITH_STDLIB
// FLOW: IN

fun nestedInfiniteRecursion() {
    val firstLambda: (Int) -> Int
    var secondLambda: ((Int) -> Int)? = null

    firstLambda = { x -> secondLambda?.invoke(x) ?: 42 }
    secondLambda = { y -> firstLambda(y) }

    val <caret>i = firstLambda(5)
}