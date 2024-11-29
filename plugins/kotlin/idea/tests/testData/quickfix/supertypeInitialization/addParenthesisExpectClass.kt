// "Change to constructor invocation" "true"
// PRIORITY: HIGH
// ENABLE_MULTIPLATFORM
// DISABLE-ERRORS

expect open class A

class B : A<caret>

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SuperClassNotInitialized$AddParenthesisFix
/* IGNORE_K2 */