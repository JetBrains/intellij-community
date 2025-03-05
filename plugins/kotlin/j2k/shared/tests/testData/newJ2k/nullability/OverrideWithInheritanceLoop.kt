// ERROR: Cycle in supertypes and/or containing declarations detected.
// ERROR: Cycle in supertypes and/or containing declarations detected.
internal open class A : B() {
    open fun foo(s: String?) {}
}

internal open class B : A() {
    open fun foo(s: String?) {}
}
