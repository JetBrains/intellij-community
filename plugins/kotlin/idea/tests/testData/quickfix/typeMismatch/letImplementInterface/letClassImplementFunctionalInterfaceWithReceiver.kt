// "Let 'Box<T>' implement interface 'Box<T>.() -> Unit'" "false"
// ERROR: Type mismatch: inferred type is Box<T> but TypeVariable(T).() -> Unit was expected
// WITH_STDLIB
// K2_AFTER_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: ARGUMENT_TYPE_MISMATCH

import kotlin.apply

class Box<T>

fun <T> use(box: Box<T>) {
    Box<T>().apply(bo<caret>x)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.LetImplementInterfaceFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.LetImplementInterfaceFixFactories$LetImplementInterfaceFix