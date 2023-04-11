package test

import dependency.*

class A

class B {
    var a b<caret>y A()
}

// MULTIRESOLVE

// REF: (for T in dependency).getValue(R, KProperty<*>)
// REF: (for T in dependency).setValue(R, KProperty<*>, Int)

// CLS_REF: (for T in dependency).getValue(R, kotlin.reflect.KProperty<*>)
// CLS_REF: (for T in dependency).setValue(R, kotlin.reflect.KProperty<*>, kotlin.Int)