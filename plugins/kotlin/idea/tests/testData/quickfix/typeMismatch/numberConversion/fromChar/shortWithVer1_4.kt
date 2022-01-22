// "Convert expression to 'Short'" "true"
// LANGUAGE_VERSION: 1.4
fun short(x: Short) {}

fun test(c: Char) {
    short(<caret>c)
}