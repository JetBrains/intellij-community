// "Specify type explicitly" "true"
package a

interface A {}

interface B {}

class C {
    val <caret>property = object : B, A {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyTypeExplicitlyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils$SpecifyExplicitTypeQuickFix