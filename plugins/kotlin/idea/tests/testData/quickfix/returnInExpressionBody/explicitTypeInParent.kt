// "Specify 'Int' return type for enclosing function 'Derived.explicitFunReturnType'" "true"
interface Base {
    fun explicitFunReturnType() : Int = 1
}

class Derived : Base {
    override fun explicitFunReturnType() = retu<caret>rn 1
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix