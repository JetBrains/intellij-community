// WITH_STDLIB

//FILE: a/a.kt
package a

val a = run {
    val a = 5
    val b = run {
        val c = 2
    }
    5
}
// PRODUCED_CLASS_NAMES: a.AKt

fun x() {
    println("")
}

//FILE: b/a.kt
package b

val b = 5

fun y() {
    println("")
}
// PRODUCED_CLASS_NAMES: b.AKt