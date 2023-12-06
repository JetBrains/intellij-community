package test

import dependency.set

fun foo(a: List<Int>) {
    a[<caret>"bar"] = 3
}

// REF: (for List<T> in dependency).set(String, T)

// CLS_REF: (for kotlin.collections.List<T> in dependency).set(kotlin.String, T)