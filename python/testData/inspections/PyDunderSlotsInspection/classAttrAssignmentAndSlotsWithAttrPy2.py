class Foo(object):
    attr = 'baz'
    __slots__ = ['attr', 'bar']

foo = Foo()
<warning descr="'Foo' object has no attribute 'attr'">foo.attr</warning> = 'spam'
print(foo.attr)

Foo.attr = 'spam' # this shadows descriptor foo.attr rendering the assignment foo.attr = 'spam' invalid
print(Foo.attr)