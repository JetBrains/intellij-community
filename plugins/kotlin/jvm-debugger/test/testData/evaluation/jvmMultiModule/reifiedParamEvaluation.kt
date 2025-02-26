// IGNORE_K1

// MODULE: jvm-lib-one
// FILE: foo1.kt

inline fun <reified T1, reified T2> foo1() {
    val x = 1
}

// MODULE: jvm-lib-two()(jvm-lib-one)
// FILE: foo2.kt

inline fun <reified T1> foo2() {
    foo1<String, Array<T1>>()
}

// MODULE: jvm-app()(jvm-lib-two)
// FILE: call.kt

fun main() {
    //Breakpoint!
    foo2<Int>()
}

// STEP_INTO: 2
// EXPRESSION: T1::class.toString() + "_" + T2::class.toString()
// RESULT: "class java.lang.String (Kotlin reflection is not available)_class [Ljava.lang.Integer; (Kotlin reflection is not available)": Ljava/lang/String;


