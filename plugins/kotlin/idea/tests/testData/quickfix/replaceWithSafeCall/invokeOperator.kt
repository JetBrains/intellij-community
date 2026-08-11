// "Replace with safe (?.) call" "true"
// WITH_STDLIB
// K2_ERROR: UNSAFE_IMPLICIT_INVOKE_CALL

val functions: Map<String, () -> Any> = TODO()

fun run(name: String) = functions[name]<caret>()
// IGNORE_K2
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix