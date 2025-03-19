// "Suppress 'UNUSED_DESTRUCTURED_PARAMETER_ENTRY' for fun test" "true"
// ACTION: Convert to single-line lambda
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Flip ',' (may change semantics)
// ACTION: Move lambda argument into parentheses
// ACTION: Rename to _
// ACTION: Specify all types explicitly in destructuring declaration
// ACTION: Specify explicit lambda signature
// ACTION: Specify type explicitly

data class Foo(val a: Int, val b: Int)

fun foo(f: (Foo) -> Unit) {
}

fun test() {
    foo { (a<caret>, b) ->
    }
}

// IGNORE_K2

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction