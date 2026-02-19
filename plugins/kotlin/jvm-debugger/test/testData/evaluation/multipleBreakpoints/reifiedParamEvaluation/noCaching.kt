// IGNORE_K1

inline fun <reified T> foo(): Int {
    //Breakpoint!
    val x = 1
    return 1
}

fun main() {
    foo<Int>() + foo<String>()
    foo<Any>()
}

// EXPRESSION: T::class.qualifiedName
// RESULT: "kotlin.Int": Ljava/lang/String;

// EXPRESSION: T::class.qualifiedName
// RESULT: "kotlin.String": Ljava/lang/String;

// EXPRESSION: T::class.qualifiedName
// RESULT: "kotlin.Any": Ljava/lang/String;