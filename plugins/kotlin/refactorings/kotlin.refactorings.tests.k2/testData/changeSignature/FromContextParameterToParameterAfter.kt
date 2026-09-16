// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class C1

fun fn(c: C1) {}

fun fn2(c: C1) {
    myWithContext(c) {
        fn(contextOf<C1>())
    }
}

fun <T> myWithContext(t: T, function: context(T)() -> Unit) {
    context(t) {
        function()
    }
}
