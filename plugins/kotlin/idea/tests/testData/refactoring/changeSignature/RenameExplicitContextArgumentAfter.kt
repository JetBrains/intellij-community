// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
class ContextParameters {

    context(s: String)
    fun foo(value: Int) {
        print(s + value)
    }

    fun test() {
        val foo = foo(1, s = "")
    }
}