// "Remove 'val' from parameter" "true"
// WITH_STDLIB
// K2_ERROR: ABSTRACT_VALUE_CLASS_CONSTRUCTOR_PROPERTY_PARAMETER
// COMPILER_ARGUMENTS: -XXLanguage:+FullValueClasses
abstract value class AbstractC2(<caret>val x: Int)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveValVarFromParameterFix
