// "Specify 'String' return type for enclosing function 'm'" "true"
// K2_AFTER_ERROR: Returns are prohibited in functions with expression body. Use block body '{...}'.
fun foo(s: String): String = s

fun m(a: String?) = foo(a ?: ret<caret>urn "" )

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix