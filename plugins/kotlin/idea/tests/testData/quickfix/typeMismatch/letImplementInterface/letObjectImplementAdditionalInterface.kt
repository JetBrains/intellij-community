// "Let the anonymous object implement interface 'A'" "true"
// K2_ERROR: Argument type mismatch: actual type is '<anonymous>', but 'A' was expected.
package let.implement

fun bar() {
    foo(<caret>object {})
}

fun foo(a: A) {
}

interface A
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.LetImplementInterfaceFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.LetImplementInterfaceFixFactories$LetImplementInterfaceFix