package test

class Foo {
  companion object {
      val fooKlass: Class<Foo> = Foo::class.java
  }
}

val SOME_FOO: Foo = Foo()
val FOO: Foo = Foo()

val some_foo: Foo = Foo()
val foo: Foo = Foo()

val SomeFoo: Foo = Foo()
val foo2 = Foo()