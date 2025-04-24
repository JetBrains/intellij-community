// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

class A {
    context(s: String)
    fun foo() {
    }

    context(<caret>a: String)
    val p: Int
        get() {
            with("a") {
                foo()
            }
            return 42
        }
}
// IGNORE_K1