// "Let 'B' implement interface 'A<Int>'" "true"
// K2_ERROR: Argument type mismatch: actual type is 'B', but 'A<Int>' was expected.
package let.implement

fun bar() {
    foo(B()<caret>)
}


fun foo(a: A<Int>) {
}

interface A<T>
class B
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.LetImplementInterfaceFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.LetImplementInterfaceFixFactories$LetImplementInterfaceFix