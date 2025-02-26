// IGNORE_K1

import kotlin.reflect.typeOf

class A<T>

inline fun <reified T> foo1() {
    //Breakpoint!
    val x = 1
}

inline fun <reified R1, reified R2> foo2() {
    foo1<Set<*>>()
    foo1<Set<R1>>()
    foo1<Map<R2, R2>>()
    foo1<Set<Array<out R2>>>()
    foo1<Map<in Set<out R1>, A<in R2>>>()
}

fun main() {
    foo2<String, Int>()
}

// EXPRESSION: typeOf<T>().toString()
// RESULT: "java.util.Set<*> (Kotlin reflection is not available)": Ljava/lang/String;

// EXPRESSION: typeOf<T>().toString()
// RESULT: "java.util.Set<java.lang.String> (Kotlin reflection is not available)": Ljava/lang/String;

// EXPRESSION: typeOf<T>().toString()
// RESULT: "java.util.Map<java.lang.Integer, java.lang.Integer> (Kotlin reflection is not available)": Ljava/lang/String;

// EXPRESSION: typeOf<T>().toString()
// RESULT: "java.util.Set<kotlin.Array<out java.lang.Integer>> (Kotlin reflection is not available)": Ljava/lang/String;

// EXPRESSION: typeOf<T>().toString()
// RESULT: "java.util.Map<in java.util.Set<out java.lang.String>, A<in java.lang.Integer>> (Kotlin reflection is not available)": Ljava/lang/String;