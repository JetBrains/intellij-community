fun foo() {
    arrayListOf(producer(), 2).count()<caret>
}

fun producer() = 42

fun <T> List<T>.count(): Int = size

// EXISTS: producer(), count(), arrayListOf(T)
