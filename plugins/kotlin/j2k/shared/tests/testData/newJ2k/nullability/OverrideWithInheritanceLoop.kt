// ERROR: CYCLIC_INHERITANCE_HIERARCHY
// ERROR: CYCLIC_INHERITANCE_HIERARCHY
internal open class A : B() {
    open fun foo(s: String?) {}
}

internal open class B : A() {
    open fun foo(s: String?) {}
}
