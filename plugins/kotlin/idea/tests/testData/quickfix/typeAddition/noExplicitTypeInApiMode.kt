// "Specify return type explicitly" "true"
// COMPILER_ARGUMENTS: -Xexplicit-api=strict
// ERROR: Visibility must be specified in explicit API mode
// ERROR: Visibility must be specified in explicit API mode
package a

interface A {
    fun <caret>foo() = ""
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyTypeExplicitlyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.SpecifyExplicitTypeFixFactories$SpecifyExplicitTypeQuickFix