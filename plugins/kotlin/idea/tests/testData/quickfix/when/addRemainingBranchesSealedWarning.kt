// "Add remaining branches" "true"
// ERROR: Unresolved reference: TODO
// ERROR: Unresolved reference: TODO
sealed class Base {
    class A : Base()
    class B : Base()
    class C : Base()
}

fun test(base: Base, x: String?) {
    x ?: when<caret> (base) {
        is Base.A -> return
    }
}
