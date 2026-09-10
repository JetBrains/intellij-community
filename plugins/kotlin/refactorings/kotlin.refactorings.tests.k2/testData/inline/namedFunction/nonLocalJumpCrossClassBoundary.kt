// COMPILER_ARGUMENTS: -Xnon-local-break-continue

fun bar() {
    for (i in 0..10) {
        class C {
            init {
                foo {
                    break
                }
            }
        }
    }
}

inline fun foo<caret>(body: () -> Unit) {
    while (true) {
        body()
    }
}
