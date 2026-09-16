// "Remove 'val' from parameter" "true"
// WITH_STDLIB
// K2_ERROR: SEALED_VALUE_CLASS_CONSTRUCTOR_PROPERTY_PARAMETER
// COMPILER_ARGUMENTS: -XXLanguage:+FullValueClasses
sealed value class Sealed(<caret>val x: Int)

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveValVarFromParameterFix
