// CHECK_ERRORS_AFTER
interface A {
    val parent: A?
}

fun ext(a: A): Int = 1 + (a.parent?.let { ext(it) } ?: 0)
