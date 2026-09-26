// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
class ContextParameters {

    context(s: String, text2: String)
    fun foo(value: Int) {
        print(s + value)
    }

    fun test() {
        val foo = foo( text2 = "", value = 1, s = "")
    }
}