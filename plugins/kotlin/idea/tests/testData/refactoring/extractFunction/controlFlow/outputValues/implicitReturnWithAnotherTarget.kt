inline fun foo2(p: () -> Int): Int = p()
inline fun foo3(p: () -> Int) {}
fun bar() {
    foo3 {
        foo2 {
            <selection>
                if (true) {
                    return@foo3 2
                }
            3
            </selection>
        }
        7
    }
}

// KT-67622
// IGNORE_K2