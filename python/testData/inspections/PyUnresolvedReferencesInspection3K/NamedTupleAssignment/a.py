from b import Foo

foo = Foo()
print(foo.bar, foo.<warning descr="Unresolved attribute reference 'baz' for class 'Foo'">baz</warning>)