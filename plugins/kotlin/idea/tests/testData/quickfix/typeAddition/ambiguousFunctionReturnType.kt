// "Specify return type explicitly" "true"
package a

interface A {}

interface B {}

class C {
    fun <caret>property() = object : B, A {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyTypeExplicitlyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.SpecifyExplicitTypeFixFactories$SpecifyExplicitTypeQuickFix