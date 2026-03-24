// "Specify 'Int' return type for enclosing function 'Derived.implicitFunReturnType'" "true"
// K2_ERROR: Return type mismatch: expected 'Nothing', actual 'Int'.
// K2_ERROR: Returns are prohibited in functions with expression body and without explicit return type. Use block body '{...}' or add an explicit return type.
interface Base {
    fun implicitFunReturnType() = 1
}

class Derived : Base {
    override fun implicitFunReturnType() = ret<caret>urn 1
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix