fun test() {
    foo() ?<caret>: bar()
}

fun foo(): String? = null
fun bar(): String? = "bar"

// EXPECTED: foo() ?: bar()