// "Specify type explicitly" "true"
// K2_ERROR: AMBIGUOUS_ANONYMOUS_TYPE_INFERRED
package a

interface A {}

interface B {}

class C {
    val <caret>property = object : B, A {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyTypeExplicitlyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils$SpecifyExplicitTypeQuickFix