// "Change type of 'z' to 'T & Any'" "true"
// ERROR: Type mismatch: inferred type is T but T & Any was expected
// LANGUAGE_VERSION: 1.8
package a

fun <T> foo(x: T & Any) {}

fun <T> bar(x: T) {
    val z: T = x
    foo(z<caret>)
}
