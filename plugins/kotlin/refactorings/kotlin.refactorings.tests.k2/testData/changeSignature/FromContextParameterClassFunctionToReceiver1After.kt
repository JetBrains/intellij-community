// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class C1

fun C1.fn() {}

fun fn2(c: C1) {
    myWithContext(c) {
        contextOf<C1>().fn()
    }
}

fun <T> myWithContext(t: T, function: context(T)() -> Unit) {
    context(t) {
        function()
    }
}
