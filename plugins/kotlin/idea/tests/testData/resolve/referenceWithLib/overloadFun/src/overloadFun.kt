package test

import dependency.*

fun foo() {
    t<caret>est("")
}

// REF: (dependency).test(String)

// CLS_REF: (dependency).test(String)