// "Let the anonymous object implement interface 'Runnable'" "true"

fun bar(): Runnable {
    return object: {}<caret>
}

interface Runnable
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.LetImplementInterfaceFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.LetImplementInterfaceFixFactories$LetImplementInterfaceFix