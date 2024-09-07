// "Remove redundant 'out' modifier" "true"
class Foo<out T> {
    val x = 0
}

fun bar(x : Foo<out<caret>  Any>) {

}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase