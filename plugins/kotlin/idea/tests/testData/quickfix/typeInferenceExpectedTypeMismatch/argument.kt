// "Remove '.java'" "true"
// PRIORITY: HIGH
// WITH_STDLIB
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
fun foo() {
    bar(Foo::class.java<caret>)
}

class Foo

fun bar(kc: kotlin.reflect.KClass<Foo>) {
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertClassToKClassFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertClassToKClassFix