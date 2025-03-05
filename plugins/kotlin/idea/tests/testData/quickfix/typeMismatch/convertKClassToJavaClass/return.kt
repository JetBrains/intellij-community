// "Convert 'KClass' to 'Class'" "true"
// PRIORITY: HIGH
// WITH_STDLIB

fun foo(): Class<*> {
    return Foo::class<caret>
}

class Foo

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertKClassToClassFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertKClassToClassFix