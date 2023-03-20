fun a() {
    charArrayOf('1', '2').m<caret>ap { it }
}

// REF: (kotlin.collections.map) public inline fun <R> CharArray.map(transform: (Char) -> R): List<R>
