// "class org.jetbrains.kotlin.idea.quickfix.ChangeParameterTypeFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeParameterTypeFix" "false"
// ERROR: Type mismatch: inferred type is String but Int was expected
// K2_AFTER_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
interface A {
    fun f(i: Int): Boolean
}

open class AA {
    fun f(i: Int) = true
}

class AAA: AA(), A

val c = AAA().f("<caret>")
