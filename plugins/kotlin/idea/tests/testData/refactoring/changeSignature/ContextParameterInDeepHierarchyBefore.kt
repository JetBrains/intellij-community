// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
open class Base {
    context(c1: Int, c2: String)
    open fun foo() {}
}

open class Intermediate : Base() {
    context(c1: Int, c2: String)
    override fun foo() {}
}

class Derived : Intermediate() {
    context(c<caret>1: Int, c2: String)
    override fun foo() {}
}