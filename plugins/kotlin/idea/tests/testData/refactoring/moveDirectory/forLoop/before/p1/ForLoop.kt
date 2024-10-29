package p1

interface MovingIterator {
    operator fun iterator(): Iterator<String>
}

fun iterateIt(f: MovingIterator) {
    for (i in f) {}
}