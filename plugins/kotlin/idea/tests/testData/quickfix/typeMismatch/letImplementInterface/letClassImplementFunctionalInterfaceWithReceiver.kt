// "Let 'Box<T>' implement interface 'Box<T>.() -> Unit'" "false"
// ERROR: Type mismatch: inferred type is Box<T> but TypeVariable(T).() -> Unit was expected
// K2_AFTER_ERROR: Argument type mismatch: actual type is 'Box<T (of fun <T> use)>', but 'Box<T (of fun <T> use)>.() -> Unit' was expected.
// WITH_STDLIB

import kotlin.apply

class Box<T>

fun <T> use(box: Box<T>) {
    Box<T>().apply(bo<caret>x)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.LetImplementInterfaceFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.LetImplementInterfaceFixFactories$LetImplementInterfaceFix