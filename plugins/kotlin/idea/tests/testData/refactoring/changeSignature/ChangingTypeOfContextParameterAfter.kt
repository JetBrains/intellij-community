// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
interface I {
    context(a: String)
    fun foo()
}

class A: I {
    context(a: String)
    override fun foo() {
        TODO("not implemented")
    }
}