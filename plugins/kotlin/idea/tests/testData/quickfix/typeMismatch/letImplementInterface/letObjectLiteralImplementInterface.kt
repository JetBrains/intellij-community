// "Let the anonymous object implement interface 'Runnable'" "true"

fun foo(r: Runnable) {}

fun bar() {
    foo(<caret>object: {})
}

interface Runnable
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.LetImplementInterfaceFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.LetImplementInterfaceFixFactories$LetImplementInterfaceFix