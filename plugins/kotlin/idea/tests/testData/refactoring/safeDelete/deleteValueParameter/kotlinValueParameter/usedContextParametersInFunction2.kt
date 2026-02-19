// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

interface A
interface B: A

context(b: B)
fun foo() {}

context(<caret>a: A)
fun bar() {
    if (a is B) {
        foo()
    }
}
// IGNORE_K1