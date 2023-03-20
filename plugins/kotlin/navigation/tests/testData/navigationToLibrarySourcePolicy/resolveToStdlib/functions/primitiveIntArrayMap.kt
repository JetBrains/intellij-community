fun a() {
    intArrayOf(1, 1).m<caret>ap { it }
}

// REF: (kotlin.collections.map) public inline fun <R> IntArray.map(transform: (Int) -> R): List<R>