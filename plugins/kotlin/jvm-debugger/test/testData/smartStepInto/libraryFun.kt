fun foo() {
    arrayListOf(1, 2).count()<caret>
}

fun <T> List<T>.count(): Int = size

// EXISTS: arrayListOf(vararg Int), count()
// IGNORE_K2