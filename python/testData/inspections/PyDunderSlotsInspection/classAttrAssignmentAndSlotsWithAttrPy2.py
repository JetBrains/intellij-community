class Foo(object):
    attr = 'baz'
    __slots__ = ['attr', 'bar']

Foo.attr = 'spam'
print(Foo.attr)

foo = Foo()
<warning descr="'Foo' object attribute 'attr' is read-only">foo.attr</warning> = 'spam'
print(foo.attr)