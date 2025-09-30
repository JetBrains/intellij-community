class Foo(object):
    attr = 'baz'
    __slots__ = [<warning descr="'attr' in __slots__ conflicts with a class variable">'attr'</warning>, 'bar']

Foo.attr = 'spam'
print(Foo.attr)

foo = Foo()
<warning descr="'Foo' object has no attribute 'attr'">foo.attr</warning> = 'spam'
print(foo.attr)