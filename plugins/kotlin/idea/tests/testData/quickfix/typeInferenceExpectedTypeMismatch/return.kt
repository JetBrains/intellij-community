// "Remove '.java'" "true"
// PRIORITY: HIGH
// WITH_STDLIB
fun foo(): kotlin.reflect.KClass<Foo> {
    return Foo::class.java<caret>
}

class Foo
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertClassToKClassFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertClassToKClassFix