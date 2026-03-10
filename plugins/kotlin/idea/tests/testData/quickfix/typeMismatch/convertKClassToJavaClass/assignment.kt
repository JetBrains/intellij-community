// "Convert 'KClass' to 'Class'" "true"
// PRIORITY: HIGH
// WITH_STDLIB
// K2_ERROR: Assignment type mismatch: actual type is 'KClass<String>', but 'Class<*>' was expected.

fun foo() {
    val clazz = String::class
    val cls: Class<*>
    cls = clazz<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertKClassToClassFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertKClassToClassFix