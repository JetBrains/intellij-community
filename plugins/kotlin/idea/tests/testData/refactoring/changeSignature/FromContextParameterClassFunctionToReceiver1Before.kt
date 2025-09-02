// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class C1

context(c: C1)
fun f<caret>n() {}

fun fn2(c: C1) {
    myWithContext(c) {
        fn()
    }
}

fun <T> myWithContext(t: T, function: context(T)() -> Unit) {
    context(t) {
        function()
    }
}