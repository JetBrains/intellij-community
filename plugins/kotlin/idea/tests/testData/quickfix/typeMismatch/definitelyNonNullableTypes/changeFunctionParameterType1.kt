// "Change parameter 'x' type of function 'bar' to 'T & Any'" "true"
// LANGUAGE_VERSION: 1.8
package a

fun <T> foo(x: T & Any) {}

fun <T> bar(x: T) {
    foo(x<caret>)
}
