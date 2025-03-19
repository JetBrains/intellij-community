inline fun <T> A.foo(t: T, noinline assertionCreator: E<T>.() -> Unit) = bar(t, assertionCreator)
inline fun <T> A.bar(t: T, noinline assertionCreator: E<T>.() -> Unit) = 1

fun test(){
    A().f<caret>oo("a") { }
}

class A
class E<T>

// IGNORE_K1