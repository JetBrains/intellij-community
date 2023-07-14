// "Change to function invocation" "true"
package a

fun foo() {}

fun test() {
    foo<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToFunctionInvocationFix