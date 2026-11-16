// FILE: Base.kt
// NEW_NAME: bar
open class Base {
    open fun foo<caret>() {}
}

// FILE: Derived.kt
class Derived : Base() {
    override fun foo() {}
}
