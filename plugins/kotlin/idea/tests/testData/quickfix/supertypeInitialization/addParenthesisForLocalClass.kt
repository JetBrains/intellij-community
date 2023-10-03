// "Change to constructor invocation" "true"
fun bar() {
    abstract class Foo {}
    class A : <caret>Foo {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SuperClassNotInitialized$AddParenthesisFix

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.SuperClassNotInitializedFactories$AddParenthesisFix
