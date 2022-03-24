// "Convert expression to 'Float'" "true"
// WITH_STDLIB
fun float(x: Float) {}

fun test(c: Char) {
    float(<caret>c)
}