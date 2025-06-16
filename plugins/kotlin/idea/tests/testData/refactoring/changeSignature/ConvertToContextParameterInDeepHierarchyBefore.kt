// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
interface IFace {
    context(i: Int)
    fun f<caret>oo(s: String, d: Double)
}

open class OpenClass : IFace {
    context(i: Int)
    override fun foo(s: String, d: Double) {}
}

class FinalClass : OpenClass(), IFace {
    context(i: Int)
    override fun foo(s: String, d: Double) {}
}