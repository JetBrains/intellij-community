// SUGGESTED_NAMES: i, getM
suspend fun foo(n: Int) = n

suspend fun test() {
    // SIBLING:
    val m = <selection>foo(1)</selection>
}

// IGNORE_K1

