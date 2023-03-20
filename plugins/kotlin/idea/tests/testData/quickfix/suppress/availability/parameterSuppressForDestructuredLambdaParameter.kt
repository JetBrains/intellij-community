// "Suppress 'UNUSED_DESTRUCTURED_PARAMETER_ENTRY' for parameter <anonymous>" "false"
// ACTION: Convert to single-line lambda
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Move lambda argument into parentheses
// ACTION: Rename to _
// ACTION: Show function parameter type hints
// ACTION: Specify all types explicitly in destructuring declaration
// ACTION: Specify explicit lambda signature
// ACTION: Specify type explicitly

data class Foo(val a: Int, val b: Int)

fun foo(f: (Foo) -> Unit) {
}

fun test() {
    foo { (<caret>a, b) ->
    }
}

// IGNORE_FIR
