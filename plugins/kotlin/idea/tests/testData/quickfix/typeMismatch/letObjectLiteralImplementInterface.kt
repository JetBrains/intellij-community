// "Let the anonymous object implement interface 'Runnable'" "true"

fun foo(r: Runnable) {}

fun bar() {
    foo(<caret>object: {})
}

interface Runnable
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.LetImplementInterfaceFix
/* IGNORE_K2 */