// "Specify 'Int' type for enclosing property 'Derived.implicitPropertyReturnType'" "true"
// K2_AFTER_ERROR: Returns are prohibited in functions with expression body. Use block body '{...}'.

interface Base {
    val implicitPropertyReturnType
        get() = 1
}

class Derived : Base {
    override val implicitPropertyReturnType
        get() = retu<caret>rn 1
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix