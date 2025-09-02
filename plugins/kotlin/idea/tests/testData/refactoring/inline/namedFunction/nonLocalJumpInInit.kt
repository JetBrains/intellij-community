// COMPILER_ARGUMENTS: -Xnon-local-break-continue
// IGNORE_K1

class C {
    init {
        for (i in 0..10) {
            foo {
                break
            }
        }
    }
}

inline fun foo<caret>(body: () -> Unit) {
    while (true) {
        body()
    }
}
