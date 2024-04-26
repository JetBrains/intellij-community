package test

import dependency.*

class A

class B {
    var a b<caret>y A()
}

// MULTIRESOLVE

// REF: (dependency).T.getValue(R, KProperty<*>)
// REF: (dependency).T.setValue(R, KProperty<*>, Int)

// CLS_REF: (dependency).T.getValue(R, KProperty<*>)
// CLS_REF: (dependency).T.setValue(R, KProperty<*>, Int)