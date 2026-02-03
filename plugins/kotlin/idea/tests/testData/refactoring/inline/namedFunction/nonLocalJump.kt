// COMPILER_ARGUMENTS: -Xnon-local-break-continue
// IGNORE_K1

fun main() {
    for (i in 1..10) {
        foo {
            continue
        }
    }
    for (i in 1..10) {
        foo {
            break
        }
    }
}

inline fun foo<caret>(body: () -> Unit) {
    while (true) {
        body()
    }
}
