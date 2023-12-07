// ATTACH_LIBRARY: contexts
// ENABLED_LANGUAGE_FEATURE: ContextReceivers

interface A

context(A)
fun bar() = "bar"

fun main() {
    with(object : A {}) {
        //Breakpoint!
        println()
    }
}

// EXPRESSION: bar()
// RESULT: "bar": Ljava/lang/String;
