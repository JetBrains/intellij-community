// COMPILER_ARGUMENTS: -Xnon-local-break-continue

fun main() {
    foo {
        for (i in 1..10) {
            break
        }
    }

    foo {
        for (i in 1..10) {
            continue
        }
    }
}

inline fun foo<caret>(body: () -> Unit) {
    while (true) {
        body()
    }
}
