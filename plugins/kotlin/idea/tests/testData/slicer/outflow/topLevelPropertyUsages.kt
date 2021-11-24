// FLOW: OUT
// WITH_STDLIB

val <caret>x = 1

val y = x

fun test() {
    val y = x

    val z: Int

    run {
        z = x

        bar(x)
    }
}

fun bar(m: Int) {

}
