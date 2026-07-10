// "Remove branch" "true"
open class Parent

class AChild : Parent()
class BChild : Parent()

fun f(p: Parent): Unit {
    when(p) {
        is AChild -> TODO()
        is BChild -> TODO()
        is BC<caret>hild -> TODO()
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveWhenBranchFix