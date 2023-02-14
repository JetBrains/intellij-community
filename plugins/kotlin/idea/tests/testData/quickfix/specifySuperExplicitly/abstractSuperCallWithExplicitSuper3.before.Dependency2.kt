package two

import one.Foo

abstract class Base {
    abstract fun check(): String
}

abstract class Derived : Base(), Foo
