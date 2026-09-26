// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
open class Base {
    context(c2: String)
    open fun foo(c1: Int) {}
}

open class Intermediate : Base() {
    context(c2: String)
    override fun foo(c1: Int) {}
}

class Derived : Intermediate() {
    context(c2: String)
    override fun foo(c1: Int) {}
}