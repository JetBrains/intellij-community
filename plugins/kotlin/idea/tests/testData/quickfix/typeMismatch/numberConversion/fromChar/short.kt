// "Convert expression to 'Short'" "true"
// WITH_STDLIB
fun short(x: Short) {}

fun test(c: Char) {
    short(<caret>c)
}