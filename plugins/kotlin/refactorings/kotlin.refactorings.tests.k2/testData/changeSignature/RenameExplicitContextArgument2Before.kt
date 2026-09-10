// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
class ContextParameters {

    context(t<caret>ext: String, text2: String)
    fun foo(value: Int) {
        print(text + value)
    }

    fun test() {
        val foo = foo( text2 = "", value = 1, text = "")
    }
}