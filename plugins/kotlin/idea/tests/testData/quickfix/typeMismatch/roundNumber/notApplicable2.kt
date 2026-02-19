// "Round using roundToInt()" "false"
// DISABLE_ERRORS
// ACTION: Change parameter 'x' type of function 'foo' to 'Long'
// ACTION: Convert expression to 'Int'
// ACTION: Create function 'foo'
// WITH_STDLIB
fun test(l: Long) {
    foo(l<caret>)
}

fun foo(x: Int) {}