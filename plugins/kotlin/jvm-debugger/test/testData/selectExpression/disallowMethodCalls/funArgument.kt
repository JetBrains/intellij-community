fun foo() {
    val a = 1
    bar(<caret>a)
}

fun bar(i: Int) = 1

// DISALLOW_METHOD_CALLS
// EXPECTED: a