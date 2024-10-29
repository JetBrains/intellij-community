// "Remove redundant 'in' modifier" "true"
class Foo<in T> {
    val x = 0
}

fun bar(x : Foo<in<caret>  Any>) {

}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase