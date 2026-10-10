// SAFE_DELETE_OPTIONS: kotlinDeleteSuperDeclarations=true
package test

open class Base {
    open val p: Int = 1
}

class Impl : Base() {
    override val <caret>p: Int = 2
}
