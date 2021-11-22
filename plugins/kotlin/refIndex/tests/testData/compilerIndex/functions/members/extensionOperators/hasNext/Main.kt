class Main {
    operator fun Int.iterator() = 42
    operator fun Int.has<caret>Next() = false
    operator fun Int.next() = 3
}
