// "Convert 'KClass' to 'Class'" "true"
// PRIORITY: HIGH
// WITH_STDLIB

fun main() {
    val clazz = String::class
    acceptClass(<caret>clazz)
}

fun acceptClass(cls: Class<*>) = Unit

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertKClassToClassFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertKClassToClassFix