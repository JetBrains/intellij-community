// "Convert 'KClass' to 'Class'" "true"
// WITH_STDLIB

fun main() {
    acceptClass(<caret>String::class)
}

fun acceptClass(cls: Class<*>) = Unit

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertKClassToClassFix