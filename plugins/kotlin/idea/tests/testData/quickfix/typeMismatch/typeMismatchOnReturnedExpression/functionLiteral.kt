// "Change return type of enclosing function '<anonymous>' to 'Int'" "false"
// WITH_STDLIB
// ERROR: Type mismatch: inferred type is Int but Unit was expected
// ERROR: Type mismatch: inferred type is Int but Unit was expected
// K2_AFTER_ERROR: RETURN_TYPE_MISMATCH
// K2_AFTER_ERROR: RETURN_TYPE_MISMATCH
// K2_ERROR: RETURN_TYPE_MISMATCH
// K2_ERROR: RETURN_TYPE_MISMATCH
fun main() {
    foo {
        return@foo bar<caret>()
    }
}

fun foo(block: () -> Unit) = block()

fun bar() = 42