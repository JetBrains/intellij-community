// "Let 'B' implement interface 'A'" "true"
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
package let.implement

fun bar() {
    foo(B()<caret>)
}


fun foo(a: A?) {
}

interface A
class B
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.LetImplementInterfaceFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.LetImplementInterfaceFixFactories$LetImplementInterfaceFix