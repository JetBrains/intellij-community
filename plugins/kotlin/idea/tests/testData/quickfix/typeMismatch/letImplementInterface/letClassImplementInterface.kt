// "Let 'B' implement interface 'A'" "true"
// WITH_STDLIB
package let.implement

fun bar() {
    foo(B()<caret>)
}


fun foo(a: A) {
}

interface A {
    fun gav()
}
class B
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.LetImplementInterfaceFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.LetImplementInterfaceFixFactories$LetImplementInterfaceFix