// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
class ContextParameters {

    context(x: String)
    fun foo2(a: String): String {
        return x + a
    }

    fun main() {
        foo2(x = <selection>"Hello"</selection>, a = "World")
    }

}