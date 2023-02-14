// "Change return type of called function 'foo' to 'Int'" "true"
// WITH_STDLIB
fun test(i: Int) {
    when (i) {
        <caret>foo() -> {}
    }
}

fun foo(): String = TODO()