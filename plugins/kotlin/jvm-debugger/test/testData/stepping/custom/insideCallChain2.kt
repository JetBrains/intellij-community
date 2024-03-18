package insideCallChain2

class It(var x: Int) {
    fun nextA() = It(x + 1)
    fun nextB() = It(x + 1)
    fun nextC() = It(x + 1)
    fun nextD() = It(x + 1)
}

fun main() {
    It(1).nextA().nextB()
        // SMART_STEP_INTO_BY_INDEX: 1
        // RESUME: 1
        //Breakpoint!, lambdaOrdinal = -1
        .nextC().nextD()

    It(1).nextA().nextB()
        // SMART_STEP_INTO_BY_INDEX: 2
        // RESUME: 1
        //Breakpoint!, lambdaOrdinal = -1
        .nextC().nextD()
}

// IGNORE_K2
