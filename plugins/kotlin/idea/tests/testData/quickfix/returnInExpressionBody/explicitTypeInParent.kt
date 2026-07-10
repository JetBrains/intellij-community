// "Specify 'Int' return type for enclosing function 'Derived.explicitFunReturnType'" "true"
// K2_ERROR: RETURN_IN_FUNCTION_WITH_EXPRESSION_BODY_AND_IMPLICIT_TYPE
// K2_ERROR: RETURN_TYPE_MISMATCH
interface Base {
    fun explicitFunReturnType() : Int = 1
}

class Derived : Base {
    override fun explicitFunReturnType() = retu<caret>rn 1
}


// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix