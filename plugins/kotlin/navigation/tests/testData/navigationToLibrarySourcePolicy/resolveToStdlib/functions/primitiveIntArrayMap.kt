fun a() {
    intArrayOf(1, 1).m<caret>ap { it }
}

// REF: (kotlin.collections.map @ jar://kotlin-stdlib-sources.jar!/commonMain/generated/_Arrays.kt) public inline fun <R> IntArray.map(transform: (Int) -> R): List<R>