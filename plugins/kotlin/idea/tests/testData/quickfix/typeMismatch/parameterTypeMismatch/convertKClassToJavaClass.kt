// "Convert 'KClass' to 'Class'" "true"
// WITH_STDLIB

fun main() {
    acceptClass(<caret>String::class)
}

fun acceptClass(cls: Class<*>) = Unit
/* IGNORE_K2 */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertKClassToClassFix