// "Add else branch" "true"
sealed class Base {
    class A : Base()
    class B : Base()
    class C : Base()
}

fun test(base: Base) {
    when<caret> (base) {
        is Base.A -> ""
    }
}