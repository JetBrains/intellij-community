package test

import dependency.*

fun foo(f: Foo<Int>) {
    for (elem i<caret>n f) {
    }
}

// MULTIRESOLVE

// REF: (for Foo<T> in dependency).iterator()
// REF: (for FooIterator<T> in dependency).hasNext()
// REF: (for FooIterator<T> in dependency).next()

// CLS_REF: (for dependency.Foo<T> in dependency).iterator()
// CLS_REF: (for dependency.FooIterator<T> in dependency).hasNext()
// CLS_REF: (for dependency.FooIterator<T> in dependency).next()