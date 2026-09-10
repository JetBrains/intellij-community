// NEW_NAME: s
// RENAME: member
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
class ContextParameters {

    context(t<caret>ext: String)
    fun foo(value: Int) {
        print(text + value)
    }

    fun test() {
        val foo = foo(1, text = "")
    }
}