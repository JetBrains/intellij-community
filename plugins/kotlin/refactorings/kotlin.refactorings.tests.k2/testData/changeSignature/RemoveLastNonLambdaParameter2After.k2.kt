operator fun Any.invoke(action: () -> Unit) {}

fun foo(): Int = 42

fun test() {
    (foo()){ }
}