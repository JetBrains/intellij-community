fun foo() {
    val <caret>f = { 1 }
    bar(f)
}

fun bar(f: () -> Int) {}
