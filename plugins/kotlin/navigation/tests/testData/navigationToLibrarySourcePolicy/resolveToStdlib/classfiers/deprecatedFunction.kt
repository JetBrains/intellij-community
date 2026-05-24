fun f() {
    listOf(1 ,2).ma<caret>xBy { it }
}

// REF: (kotlin.collections.maxBy @ jar://kotlin-stdlib-sources.jar!/commonMain/generated/_Collections.kt) @SinceKotlin("1.7") @kotlin.jvm.JvmName("maxByOrThrow") @Suppress("CONFLICTING_OVERLOADS") public inline fun <T, R : Comparable<R>> Iterable<T>.maxBy(selector: (T) -> R): T
