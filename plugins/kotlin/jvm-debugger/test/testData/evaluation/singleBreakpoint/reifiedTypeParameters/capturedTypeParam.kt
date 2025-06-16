// IGNORE_K1

inline fun <reified T> foo(a: List<*>): List<T?> = a.map {
    //Breakpoint!
    it as T
}

inline fun <reified R> bar(x: R) {
    listOf(1, 2).forEach {
        foo<R>(listOf(x))
    }
}

fun main() {
    bar("str")
}

// EXPRESSION: it as T
// RESULT: "str": Ljava/lang/String;