// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
interface IFace {
    context(i: Int, s: String)
    fun foo(d: Double)
}

open class OpenClass : IFace {
    context(i: Int, s: String)
    override fun foo(d: Double) {}
}

class FinalClass : OpenClass(), IFace {
    context(i: Int, s: String)
    override fun foo(d: Double) {}
}