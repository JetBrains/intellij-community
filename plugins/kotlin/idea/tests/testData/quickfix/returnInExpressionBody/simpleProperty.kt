// "Specify 'String' type for enclosing property 'a'" "true"
// K2_ERROR: Property type 'Nothing' needs to be specified explicitly.
// K2_ERROR: Return type mismatch: expected 'Nothing', actual 'String'.
// K2_ERROR: Returns are prohibited in functions with expression body and without explicit return type. Use block body '{...}' or add an explicit return type.

val a
    get() = r<caret>eturn ""

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix