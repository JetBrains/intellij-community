// "Cast expression 'x' to 'Collection<T & Any>'" "true"
// LANGUAGE_VERSION: 1.8
fun <T> foo(x: Collection<T & Any>) {}

fun <T> bar(x: Collection<T>) {
    foo(x<caret>)
}
