class Foo(object):
    attr = 'baz'
    __slots__ = ['foo', 'bar', '__dict__']

Foo.attr = 'spam'
print(Foo.attr)

foo = Foo()
foo.attr = 'spam'
print(foo.attr)