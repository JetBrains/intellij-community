// "Convert expression to 'Int'" "true"
// WITH_STDLIB
fun foo() {
    bar("1".toLong()<caret>)
}

fun bar(l: Int) {
}