// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
interface I {
    context(<caret>a: Int)
    fun foo()
}

class A: I {
    context(a: Int)
    override fun foo() {
        TODO("not implemented")
    }
}