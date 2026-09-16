// PARAM_TYPES: suspend () -> T, kotlin.Function<T>, kotlin.Any
// PARAM_DESCRIPTOR: public fun <T> (suspend () -> T).foo(): kotlin.Unit defined in root package in file suspendReceiverType.kt
interface A {
    suspend fun foo()
}

fun <T> (suspend () -> T).foo() {
    if (<selection>this !is A</selection>) {
        // do smth
    }
}