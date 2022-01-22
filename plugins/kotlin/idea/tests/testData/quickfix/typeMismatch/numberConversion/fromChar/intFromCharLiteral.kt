// "Convert expression to 'Int'" "true"
// WITH_STDLIB
fun int(x: Int) {}

fun test(c: Char) {
    int(<caret>'c')
}