// "Remove '.java'" "true"
// WITH_STDLIB
fun foo() {
    bar(Foo::class.java<caret>)
}

class Foo

fun bar(kc: kotlin.reflect.KClass<Foo>) {
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertClassToKClassFix