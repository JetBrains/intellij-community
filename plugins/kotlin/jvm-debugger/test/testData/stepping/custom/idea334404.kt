package idea334404

fun main() {
    // This is a line + lambdas breakpoint, 4 stops expected
    //Breakpoint!
    listOf(1, 2, 3).forEach { }

    // This is a line breakpoint, 1 stop expected
    //Breakpoint!, lambdaOrdinal = -1
    listOf(1, 2, 3).forEach { }

    // This is a lambda breakpoint, 3 stops expected
    //Breakpoint!, lambdaOrdinal = 1
    listOf(1, 2, 3).forEach { }


    // This is a line + lambdas breakpoint, 4 stops expected
    //Breakpoint!
    listOf(1, 2, 3).stream().forEach { }

    // This is a line breakpoint, 1 stop expected
    //Breakpoint!, lambdaOrdinal = -1
    listOf(1, 2, 3).stream().forEach { }

    // This is a lambda breakpoint, 3 stops expected
    //Breakpoint!, lambdaOrdinal = 1
    listOf(1, 2, 3).stream().forEach { }
}

// RESUME: 30
