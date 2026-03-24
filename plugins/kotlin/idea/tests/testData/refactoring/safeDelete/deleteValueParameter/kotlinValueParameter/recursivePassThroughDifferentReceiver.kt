fun Int.fibu(<caret>unused: Int): Int =
    if (this <= 2) 1 else (this - 2).fibu(unused) + (this - 1).fibu(unused)

fun test() {
    5.fibu(0)
}
// IGNORE_K1