// NEW_NAME: str
// RENAME: member
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
class ContextParameters {

    context(text: String, tex<caret>t2: String)
    fun foo() {
        print(text + text2)
    }

    fun test() {
        val foo = foo( text = "", text2 = "")
    }
}