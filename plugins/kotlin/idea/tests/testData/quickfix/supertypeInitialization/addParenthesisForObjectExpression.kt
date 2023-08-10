// "Change to constructor invocation" "true"
fun bar() {
    abstract class Foo {}
    val foo: Foo = object : <caret>Foo {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SuperClassNotInitialized$AddParenthesisFix