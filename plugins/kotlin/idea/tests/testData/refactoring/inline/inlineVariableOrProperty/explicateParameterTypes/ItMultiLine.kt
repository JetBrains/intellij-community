fun foo() {
    val f: (Int) -> Int = {
        it
    }
    val ff = <caret>f
}
// IGNORE_K2