// IGNORE_K1

// FILE: foo1.kt

inline fun <reified T> foo1() {
    val x = 1
}

// FILE: foo2.kt

inline fun <reified T> foo2() {
    foo1<Array<Array<T>>>()
}

// FILE: foo3.kt

inline fun <reified T> foo3(x : T) {
    foo2<Array<T>>()
}

// FILE: main.kt

fun main() {
    //Breakpoint!
    foo3("")
}

// STEP_INTO: 3

// EXPRESSION: T::class.toString()
// RESULT: "class [[[Ljava.lang.String; (Kotlin reflection is not available)": Ljava/lang/String;
