// "Let 'B' implement interface 'A<Int>'" "true"
// K2_ERROR: ASSIGNMENT_TYPE_MISMATCH
package let.implement

fun bar() {
    val a: A<Int>
    a = B()<caret>
}

interface A<T>
class B
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.LetImplementInterfaceFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.LetImplementInterfaceFixFactories$LetImplementInterfaceFix