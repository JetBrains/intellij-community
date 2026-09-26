// COMPILER_ARGUMENTS: -Xnon-local-break-continue

fun main() {
    for (i in 1..10) {
        noCycles {
            continue
        }
    }
    for (i in 1..10) {
        noCycles {
            break
        }
    }
}

inline fun noCycles<caret>(body: () -> Unit) {
    body()
}
