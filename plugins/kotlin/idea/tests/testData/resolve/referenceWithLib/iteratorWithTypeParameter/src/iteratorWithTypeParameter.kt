package test

import dependency.*

fun foo(f: Foo<Int>) {
    for (elem i<caret>n f) {
    }
}

// MULTIRESOLVE

// REF: (dependency).Foo<T>.iterator()
// REF: (dependency).FooIterator<T>.hasNext()
// REF: (dependency).FooIterator<T>.next()

// CLS_REF: (dependency).Foo<T>.iterator()
// CLS_REF: (dependency).FooIterator<T>.hasNext()
// CLS_REF: (dependency).FooIterator<T>.next()