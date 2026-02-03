// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

open class A

context(s: String)
fun foo() {}

context(<caret>a: String)
fun bar() {
    object : A() {
        fun m() {
            foo()
        }
    }.m()
}
// IGNORE_K1