// SAFE_DELETE_OPTIONS: kotlinDeleteSuperDeclarations=true
package test

open class Base {
    open fun f() {}
}

class Impl : Base() {
    override fun <caret>f() {}
}