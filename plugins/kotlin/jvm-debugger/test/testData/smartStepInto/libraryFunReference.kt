fun foo() {
    arrayListOf(1, 2).also(::println).count()<caret>
}

fun <T> List<T>.count(): Int = size

// EXISTS: count(), arrayListOf(T), println(Any?)
