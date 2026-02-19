// "Remove type arguments" "true"
interface Foo<T1, T2> {
    fun f() {}
}

class Bar: Foo<Int, Boolean> {
    fun g() {
        super<Foo<Int, <caret>Boolean>>.f();
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePsiElementSimpleFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePsiElementSimpleFix