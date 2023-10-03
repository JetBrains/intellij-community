// "Change to constructor invocation" "true"
open class A(x : Int = 42, vararg y : Int) {}
class B() : A<caret> {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SuperClassNotInitialized$AddParenthesisFix

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.SuperClassNotInitializedFactories$AddParenthesisFix
