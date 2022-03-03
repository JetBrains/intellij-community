// "Change return type of enclosing function 'foo' to 'Int'" "true"
// WITH_STDLIB
fun foo(n: Int): Boolean {
    n.let {
        return@foo <caret>1
    }
}