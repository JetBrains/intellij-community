// COMPILER_ARGUMENTS: -Xnon-local-break-continue

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

fun foo<caret>(body: () -> Unit) {
    while (true) {
        body()
    }
}
