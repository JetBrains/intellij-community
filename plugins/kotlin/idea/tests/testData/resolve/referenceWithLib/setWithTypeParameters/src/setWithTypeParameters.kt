package test

import dependency.set

fun foo(a: List<Int>) {
    a[<caret>"bar"] = 3
}

// REF: (dependency).List<T>.set(String, T)

// CLS_REF: (dependency).List<T>.set(String, T)