// ERROR: Cycle formed in the inheritance hierarchy of this type.
// ERROR: Cycle formed in the inheritance hierarchy of this type.
internal open class A : B() {
    open fun foo(s: String?) {}
}

internal open class B : A() {
    open fun foo(s: String?) {}
}
