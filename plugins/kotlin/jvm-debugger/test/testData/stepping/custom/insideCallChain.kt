package insideCallChain

fun String.convert(function: (String) -> String) = function("convert: $this")
fun foo() = "three"
fun String.convert2(function: (String) -> String) = function("convert: $this")
fun foo2() = "three"

fun main() {
    "one".convert { "two $it" }
        // SMART_STEP_INTO_BY_INDEX: 1
        // RESUME: 1
        //Breakpoint!, lambdaOrdinal = -1
        .convert { foo() }
        .convert2 { foo2() }

    "one".convert { "two $it" }
        // SMART_STEP_INTO_BY_INDEX: 2
        // STEP_INTO: 1
        // RESUME: 1
        //Breakpoint!, lambdaOrdinal = -1
        .convert { foo() }
        .convert2 { foo2() }

    "one".convert { "two $it" }
        // SMART_STEP_INTO_BY_INDEX: 3
        // RESUME: 1
        //Breakpoint!, lambdaOrdinal = -1
        .convert { foo() }
        .convert2 { foo2() }

    "one".convert { "two $it" }
        // SMART_STEP_INTO_BY_INDEX: 4
        // STEP_INTO: 1
        // RESUME: 1
        //Breakpoint!, lambdaOrdinal = -1
        .convert { foo() }
        .convert2 { foo2() }
}

// IGNORE_K2
