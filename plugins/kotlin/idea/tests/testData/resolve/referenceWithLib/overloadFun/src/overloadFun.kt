package test

import dependency.*

fun foo() {
    t<caret>est("")
}

// ALLOW_AST_ACCESS

// REF: (dependency).test(String)

// CLS_REF: (dependency).test(String)