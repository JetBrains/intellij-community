// IGNORE_K1

inline fun <reified T> bar(x: Int, y: Int): Int {
    //Breakpoint!
    return 1
}

fun main() {
    bar<Int>(bar<String>(
            1,
            2
        ),
        bar<Any>(
            3,
            4
        )
    )
}

// EXPRESSION: T::class.qualifiedName
// RESULT: "kotlin.String": Ljava/lang/String;

// EXPRESSION: T::class.qualifiedName
// RESULT: "kotlin.Any": Ljava/lang/String;

// EXPRESSION: T::class.qualifiedName
// RESULT: "kotlin.Int": Ljava/lang/String;